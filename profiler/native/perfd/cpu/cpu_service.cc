/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "perfd/cpu/cpu_service.h"

#include <stdio.h>

#include "perfd/cpu/cpu_config.h"
#include "perfd/cpu/profiling_app.h"
#include "proto/common.pb.h"
#include "utils/activity_manager.h"
#include "utils/current_process.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using grpc::ServerContext;
using grpc::Status;
using grpc::StatusCode;
using profiler::proto::CpuCoreConfigRequest;
using profiler::proto::CpuCoreConfigResponse;
using profiler::proto::CpuDataRequest;
using profiler::proto::CpuDataResponse;
using profiler::proto::CpuProfilingAppStartRequest;
using profiler::proto::CpuProfilingAppStartResponse;
using profiler::proto::CpuProfilingAppStopRequest;
using profiler::proto::CpuProfilingAppStopResponse;
using profiler::proto::CpuStartRequest;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopRequest;
using profiler::proto::CpuStopResponse;
using profiler::proto::CpuTraceConfiguration;
using profiler::proto::CpuTraceInfo;
using profiler::proto::CpuTraceMode;
using profiler::proto::CpuTraceType;
using profiler::proto::CpuUsageData;
using profiler::proto::GetThreadsRequest;
using profiler::proto::GetThreadsResponse;
using profiler::proto::GetTraceInfoRequest;
using profiler::proto::GetTraceInfoResponse;
using profiler::proto::ProfilingStateRequest;
using profiler::proto::ProfilingStateResponse;
using std::map;
using std::string;
using std::vector;

namespace profiler {

grpc::Status CpuServiceImpl::GetData(ServerContext* context,
                                     const CpuDataRequest* request,
                                     CpuDataResponse* response) {
  Trace trace("CPU:GetData");
  const vector<CpuUsageData>& data =
      cache_.Retrieve(request->session().pid(), request->start_timestamp(),
                      request->end_timestamp());
  for (const auto& datum : data) {
    *(response->add_data()) = datum;
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::GetThreads(ServerContext* context,
                                        const GetThreadsRequest* request,
                                        GetThreadsResponse* response) {
  Trace trace("CPU:GetThreads");
  CpuCache::ThreadSampleResponse threads_response =
      cache_.GetThreads(request->session().pid(), request->start_timestamp(),
                        request->end_timestamp());
  // Samples containing all the activities that should be added to the response.
  const vector<ThreadsSample>& samples = threads_response.activity_samples;

  // Snapshot that should be included in the response.
  auto snapshot = threads_response.snapshot;
  if (snapshot.threads().empty()) {
    // If there are no threads in the |snapshot|, we use the snapshot of the
    // first sample from |samples|, in case it's not empty
    if (!samples.empty()) {
      *(response->mutable_initial_snapshot()) = samples.front().snapshot;
    }
  } else {
    *(response->mutable_initial_snapshot()) = snapshot;
  }

  // Threads that should be added to the response, ordered by thread id.
  // The activities detected by the sampled should be grouped by thread.
  map<int32_t, GetThreadsResponse::Thread> threads;

  for (const auto& sample : samples) {
    for (const auto& activity : sample.activities) {
      auto tid = activity.tid;
      // Add the thread to the map if it's not there yet.
      if (threads.find(tid) == threads.end()) {
        GetThreadsResponse::Thread thread;
        thread.set_tid(tid);
        thread.set_name(activity.name);
        threads[tid] = thread;
      }
      auto* thread_activity = threads[tid].add_activities();
      thread_activity->set_timestamp(activity.timestamp);
      thread_activity->set_new_state(activity.state);
    }
  }

  // Add all the threads to the response.
  for (const auto& thread : threads) {
    *(response->add_threads()) = thread.second;
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::GetTraceInfo(ServerContext* context,
                                          const GetTraceInfoRequest* request,
                                          GetTraceInfoResponse* response) {
  Trace trace("CPU:GetTraceInfo");
  string app_name = ProcessManager::GetCmdlineForPid(request->session().pid());
  const vector<ProfilingApp>& data = trace_manager_->GetCaptures(
      app_name, request->from_timestamp(), request->to_timestamp());
  for (const auto& datum : data) {
    CpuTraceInfo* info = response->add_trace_info();
    info->set_trace_type(datum.configuration.user_options().trace_type());
    info->set_trace_mode(datum.configuration.user_options().trace_mode());
    info->set_initiation_type(datum.configuration.initiation_type());
    info->set_from_timestamp(datum.start_timestamp);
    info->set_to_timestamp(datum.end_timestamp);
    info->set_trace_id(datum.trace_id);
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::StartMonitoringApp(ServerContext* context,
                                                const CpuStartRequest* request,
                                                CpuStartResponse* response) {
  int32_t pid = request->session().pid();
  if (!cache_.AllocateAppCache(pid)) {
    return Status(StatusCode::RESOURCE_EXHAUSTED,
                  "Cannot allocate a cache for CPU data");
  }
  auto status = usage_sampler_.AddProcess(pid);
  if (status == CpuStartResponse::SUCCESS) {
    status = thread_monitor_.AddProcess(pid);
  }
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuServiceImpl::StopMonitoringApp(ServerContext* context,
                                               const CpuStopRequest* request,
                                               CpuStopResponse* response) {
  int32_t pid = request->session().pid();
  auto status = usage_sampler_.RemoveProcess(pid);
  if (status == CpuStopResponse::SUCCESS) {
    status = thread_monitor_.RemoveProcess(pid);
  }
  response->set_status(status);
  DoStopProfilingApp(request->app_name(), nullptr);
  // cache_.DeallocateAppCache(pid) must happen last because prior actions such
  // as DoStopProfilingApp() depends on data from the cache to act.
  cache_.DeallocateAppCache(pid);
  return Status::OK;
}

grpc::Status CpuServiceImpl::StartProfilingApp(
    ServerContext* context, const CpuProfilingAppStartRequest* request,
    CpuProfilingAppStartResponse* response) {
  Trace trace("CPU:StartProfilingApp");
  string error;
  auto* capture = trace_manager_->StartProfiling(
      clock_->GetCurrentTime(), request->configuration(), &error);
  if (capture != nullptr) {
    response->set_status(CpuProfilingAppStartResponse::SUCCESS);
  } else {
    response->set_status(CpuProfilingAppStartResponse::FAILURE);
    response->set_error_message(error);
  }

  return Status::OK;
}

grpc::Status CpuServiceImpl::StopProfilingApp(
    ServerContext* context, const CpuProfilingAppStopRequest* request,
    CpuProfilingAppStopResponse* response) {
  DoStopProfilingApp(request->app_name(), response);
  return Status::OK;
}

void CpuServiceImpl::DoStopProfilingApp(const string& app_name,
                                        CpuProfilingAppStopResponse* response) {
  proto::CpuProfilingAppStopResponse::Status status;
  string error;
  bool need_response = response != nullptr;
  ProfilingApp* capture =
      trace_manager_->StopProfiling(app_name, need_response, &status, &error);

  if (need_response) {
    if (status == CpuProfilingAppStopResponse::SUCCESS) {
      assert(capture != nullptr);
      response->set_trace_id(capture->trace_id);
      // Move over the file to the shared cached to be access via |GetBytes|
      std::ostringstream oss;
      // "cache/complete" is where the generic bytes rpc fetches contents from.
      oss << CurrentProcess::dir() << "cache/complete/" << capture->trace_id;
      DiskFileSystem fs;
      // DiskFileSystem::MoveFile returns true when it fails.
      // TODO b/133321803 save this move by having Daemon generate a path in the
      // byte cache that traces can output contents to directly.
      bool move_failed =
          fs.MoveFile(capture->configuration.temp_path(), oss.str());
      if (move_failed) {
        status = CpuProfilingAppStopResponse::CANNOT_READ_FILE;
        error = "Failed to read trace from device";
      }
    }
    response->set_status(status);
    // Empty if success but simply set it for all cases.
    response->set_error_message(error);
  }

  if (capture != nullptr) {
    // No more use of this file. Delete it.
    remove(capture->configuration.temp_path().c_str());
  }
}

grpc::Status CpuServiceImpl::CheckAppProfilingState(
    ServerContext* context, const ProfilingStateRequest* request,
    ProfilingStateResponse* response) {
  string app_name = ProcessManager::GetCmdlineForPid(request->session().pid());
  ProfilingApp* app = trace_manager_->GetOngoingCapture(app_name);

  // Whether the app is being profiled (there is a stored start profiling
  // request corresponding to the app)
  response->set_check_timestamp(clock_->GetCurrentTime());
  bool is_being_profiled = app != nullptr;
  response->set_being_profiled(is_being_profiled);

  if (is_being_profiled) {
    // App is being profiled. Include the start profiling request and its
    // timestamp in the response.
    response->set_start_timestamp(app->start_timestamp);
    *(response->mutable_configuration()) = app->configuration;
  }

  return Status::OK;
}

grpc::Status CpuServiceImpl::StartStartupProfiling(
    grpc::ServerContext* context,
    const profiler::proto::StartupProfilingRequest* request,
    profiler::proto::StartupProfilingResponse* response) {
  string error;
  ProfilingApp* capture = trace_manager_->StartProfiling(
      clock_->GetCurrentTime(), request->configuration(), &error);

  if (capture != nullptr) {
    response->set_status(proto::StartupProfilingResponse::SUCCESS);
  } else {
    response->set_status(proto::StartupProfilingResponse::FAILURE);
    response->set_error_message(error);
  }

  return Status::OK;
}

int64_t CpuServiceImpl::GetEarliestDataTime(int32_t pid) {
  string app_pkg_name = ProcessManager::GetCmdlineForPid(pid);
  ProfilingApp* app = trace_manager_->GetOngoingCapture(app_pkg_name);
  return app != nullptr ? app->start_timestamp : LLONG_MAX;
}

Status CpuServiceImpl::GetCpuCoreConfig(ServerContext* context,
                                        const CpuCoreConfigRequest* request,
                                        CpuCoreConfigResponse* response) {
  return CpuConfig::GetCpuCoreConfig(response->mutable_cpu_core_config());
}

}  // namespace profiler
