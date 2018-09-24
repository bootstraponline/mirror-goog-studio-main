/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "config.h"

#include <unistd.h>
#include <fstream>

#include "utils/log.h"

namespace deploy {

std::unique_ptr<proto::SwapRequest> ParseFromFile(
    const std::string& file_location) {
  std::fstream stream(file_location, std::ios::in | std::ios::binary);
  std::string request_string((std::istreambuf_iterator<char>(stream)),
                             std::istreambuf_iterator<char>());
  return ParseFromString(request_string);
}

std::unique_ptr<proto::SwapRequest> ParseFromString(
    const std::string& request_string) {
  auto request = std::unique_ptr<proto::SwapRequest>(new proto::SwapRequest());
  if (!request->ParseFromString(request_string)) {
    request.reset();
  }
  return request;
}

}  // namespace deploy
