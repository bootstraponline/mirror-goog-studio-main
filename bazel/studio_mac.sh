#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel
"${script_dir}/bazel" \
        --max_idle_secs=60 \
        test \
        --keep_going \
        --config=local \
        --config=cloud_resultstore \
        --invocation_id=${invocation_id} \
        --build_tag_filters=-no_mac \
        --test_tag_filters=-no_mac,-no_test_mac,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate_only \
        --profile=${dist_dir}/prof \
        -- \
        $(< "${script_dir}/targets")

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  # Create profile html in ${dist_dir} so it ends up in Artifacts.
  ${script_dir}/bazel analyze-profile --html ${dist_dir}/prof
fi

exit $bazel_status
