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

#ifndef INSTALL_SERVER_H
#define INSTALL_SERVER_H

#include <memory>
#include <string>

#include "tools/base/deploy/common/proto_pipe.h"
#include "tools/base/deploy/installer/install_client.h"
#include "tools/base/deploy/installer/workspace.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

// Object that can be used to run an install server in the current process.
class InstallServer {
 public:
  InstallServer(int input_fd, int output_fd)
      : input_(input_fd), output_(output_fd) {}

  // Runs an install server in this process. This blocks until the server
  // finishes running.
  void Run();

 private:
  ProtoPipe input_;
  ProtoPipe output_;

  bool WriteStatus(proto::InstallServerResponse::Status status);
};

// Starts an install server in a new process.
std::unique_ptr<InstallClient> StartServer(const Workspace& workspace,
                                           const std::string& server_path,
                                           const std::string& package_name);

}  // namespace deploy

#endif