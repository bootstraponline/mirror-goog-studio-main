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

#ifndef PERFD_CPU_SIMPLEPERFMANAGER_H_
#define PERFD_CPU_SIMPLEPERFMANAGER_H_

#include <map>
#include <mutex>
#include <string>

#include "perfd/cpu/simpleperf.h"
#include "utils/clock.h"

namespace profiler {

// Entry storing all data related to an ongoing profiling.
struct OnGoingProfiling {
  // App pid being profiled.
  int pid;
  // Simpleperf pid doing the profiling.
  int simpleperf_pid;
  // The ABI CPU architecture (e.g. arm, arm64, x86, x86_64) corresponding to
  // the simpleperf binary being used to profile.
  std::string abi_arch;
  // File path where trace will be made available.
  std::string trace_path;
  // File path of the raw trace generated by running simpleperf record, which is
  // later converted into protobuf format
  std::string raw_trace_path;
  // File name pattern for trace, raw trace and log.
  std::string output_prefix;
  // If something happen while simpleperf is running, store logs in this file.
  std::string log_file_path;
};

class SimpleperfManager {
 public:
  explicit SimpleperfManager(const Clock &clock, const Simpleperf &simpleperf)
      : clock_(clock), simpleperf_(simpleperf) {}
  ~SimpleperfManager();

  // Returns true if profiling of app |app_name| was started successfully.
  // |trace_path| is also set to where the trace file will be made available
  // once profiling of this app is stopped. To call this method on an already
  // profiled app is a noop. The simpleperf binary used to profile should
  // correspond to the given |abi_arch|. If |is_startup_profiling| is true,
  // it means that the application hasn't launched and pid is not available
  // yet, so it will use "--app" flag instead of "--pid".
  bool StartProfiling(const std::string &app_name, const std::string &abi_arch,
                      int sampling_interval_us, std::string *trace_path,
                      std::string *error, bool is_startup_profiling = false);
  // Stops simpleperf process that is currently profiling |app_name|.
  // If |need_result|, convert the raw data to the processed data in a file.
  // Always cleans up raw data file and log file.
  bool StopProfiling(const std::string &app_name, bool need_result,
                     std::string *error);
  // Returns true if the app is currently being profiled by a simpleperf
  // process.
  bool IsProfiling(const std::string &app_name);

 private:
  const Clock &clock_;
  std::map<std::string, OnGoingProfiling> profiled_;
  std::mutex start_stop_mutex_;  // Protects simpleperf start/stop
  const Simpleperf &simpleperf_;

  // Generate the filename pattern used for trace and log (a name guaranteed
  // not to collide and without an extension).
  std::string GetFileBaseName(const std::string &app_name) const;

  // Wait until simpleperf process has returned.
  bool WaitForSimpleperf(const OnGoingProfiling &ongoing_recording,
                         std::string *error) const;

  // Convert a trace file from simpleperf binary format to protobuf.
  // Source and Destination are determined by |ongoing_recording| values.
  bool ConvertRawToProto(const OnGoingProfiling &ongoing_recording,
                         std::string *error) const;

  // Delete log file and raw trace file generated by running simpleperf record.
  void CleanUp(const OnGoingProfiling &ongoing_recording) const;

  bool StopSimpleperf(const OnGoingProfiling &ongoing_recording,
                      std::string *error) const;
};
}  // namespace profiler

#endif  // PERFD_CPU_SIMPLEPERFMANAGER_H_
