/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef INSTALL_CLIENT_H
#define INSTALL_CLIENT_H

#include <memory>
#include <string>
#include <unordered_set>
#include <vector>

#include "tools/base/deploy/common/proto_pipe.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

// Client object for communicating with an install server.
class InstallClient {
 public:
  InstallClient(int server_pid, int input_fd, int output_fd)
      : server_pid_(server_pid), input_(input_fd), output_(output_fd) {}

  std::unique_ptr<proto::CheckSetupResponse> CheckSetup(
      const proto::CheckSetupRequest& req);
  std::unique_ptr<proto::OverlayUpdateResponse> UpdateOverlay(
      const proto::OverlayUpdateRequest& req);
  std::unique_ptr<proto::GetAgentExceptionLogResponse> GetAgentExceptionLog(
      const proto::GetAgentExceptionLogRequest& req);
  std::unique_ptr<proto::OpenAgentSocketResponse> OpenAgentSocket(
      const proto::OpenAgentSocketRequest& req);
  std::unique_ptr<proto::SendAgentMessageResponse> SendAgentMessage(
      const proto::SendAgentMessageRequest& req);

  // Waits indefinitely for the server to start.
  bool WaitForStart() {
    return WaitForStatus(proto::InstallServerResponse::SERVER_STARTED);
  }

  // Sends a server exit request and waits indefinitely for the server to exit.
  bool KillServerAndWait(proto::InstallServerResponse* response);

 private:
  int server_pid_;
  ProtoPipe input_;
  ProtoPipe output_;
  const int kDefaultTimeoutMs = 5000;

  bool WaitForStatus(proto::InstallServerResponse::Status status);
  std::unique_ptr<proto::InstallServerResponse> Send(
      proto::InstallServerRequest& req);

  // Writes a serialized protobuf message to the connected client.
  bool Write(const proto::InstallServerRequest& request) {
    return output_.Write(request);
  }

  // Waits up for a message to be available from the client, then attempts to
  // parse the data read into the specified proto.
  bool Read(proto::InstallServerResponse* response) {
    return input_.Read(kDefaultTimeoutMs, response);
  }
};

}  // namespace deploy

#endif