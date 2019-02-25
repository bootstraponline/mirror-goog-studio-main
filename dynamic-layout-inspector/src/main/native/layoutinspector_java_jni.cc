/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include <jni.h>
#include <sstream>
#include "agent/agent.h"
#include "agent/jni_wrappers.h"

/**
 * Native calls to send the skia picture back to studio (using an event with a
 * payload id and, separately, a payload), and error messages.
 */
extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_sendErrorMessage(
    JNIEnv *env, jclass clazz, jstring jmessage) {
  profiler::JStringWrapper message(env, jmessage);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[message, env](profiler::proto::AgentService::Stub &stub,
                      grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_is_ended(true);
        event->set_kind(profiler::proto::Event::LAYOUT_INSPECTOR);
        event->set_group_id(profiler::proto::Event::LAYOUT_INSPECTOR_ERROR);
        auto *inspector_event = event->mutable_layout_inspector_event();
        inspector_event->set_error_message(message.get().c_str());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_sendSkiaPicture(
    JNIEnv *env, jclass clazz, jbyteArray jmessage, jint jlen, jint id) {
  profiler::JByteArrayWrapper message(env, jmessage, jlen);

  std::stringstream ss;
  ss << id;
  std::string payload_name = ss.str();

  profiler::Agent::Instance().SubmitAgentTasks(
      {[message, payload_name](profiler::proto::AgentService::Stub &stub,
                               grpc::ClientContext &ctx) mutable {
         profiler::proto::EmptyResponse response;
         profiler::proto::SendPayloadRequest payload;
         payload.set_name(payload_name);
         payload.set_payload(message.get().data(), message.length());
         payload.set_is_partial(false);
         return stub.SendPayload(&ctx, payload, &response);
       },
       [id](profiler::proto::AgentService::Stub &stub,
            grpc::ClientContext &ctx) mutable {
         profiler::proto::SendEventRequest request;
         auto *event = request.mutable_event();
         event->set_is_ended(true);
         event->set_kind(profiler::proto::Event::LAYOUT_INSPECTOR);
         event->set_group_id(profiler::proto::Event::SKIA_PICTURE);
         auto *inspector_event = event->mutable_layout_inspector_event();
         inspector_event->set_payload_id(id);
         profiler::proto::EmptyResponse response;
         return stub.SendEvent(&ctx, request, &response);
       }});
}
}
