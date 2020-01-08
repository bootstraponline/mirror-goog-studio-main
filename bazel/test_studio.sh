#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

build_tag_filters=-no_linux
test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate

config_options="--config=remote"

# Building //tools/adt/idea/android:artifacts creates files referenced from
# IMLs, which is required for iml_to_build to succeed below.
"${script_dir}/bazel" \
  build \
  ${config_options} \
  //tools/adt/idea/android:artifacts

"${script_dir}/bazel" \
  run \
  ${config_options} \
  //tools/base/bazel:iml_to_build -- --dry_run --warnings_as_errors

readonly iml_to_build_status=$?
if [ $iml_to_build_status -ne 0 ]; then
  echo "BUILD files not in sync with *.iml files, run 'bazel run //tools/base/bazel:iml_to_build' to update them."
  exit $iml_to_build_status
fi

# Generate a UUID for use as the bazel test invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json.gz" \
  -- \
  //tools/idea/updater:updater_deploy.jar \
  //tools/base/bazel:perfgate_logs_collector_deploy.jar \
  //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector_deploy.jar \
  $(< "${script_dir}/targets")

readonly bazel_status=$?

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html

  readonly java="prebuilts/studio/jdk/linux/jre/bin/java"
  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  readonly bin_dir="$("${script_dir}"/bazel info ${config_options} bazel-bin)"

  ${java} -jar "${bin_dir}/tools/vendor/adt_infra_internal/rbe/logscollector/logs-collector_deploy.jar" \
    -bes "${DIST_DIR}/bazel-${BUILD_NUMBER}.bes" \
    -testlogs "${DIST_DIR}/logs/junit" \
    -perfzip "${DIST_DIR}/perfgate_data.zip"

  cp -a ${bin_dir}/tools/idea/updater/updater_deploy.jar ${DIST_DIR}/android-studio-updater.jar
  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skiaparser.zip ${DIST_DIR}
  cp -a ${bin_dir}/tools/base/sdklib/commandlinetools_*.zip "${DIST_DIR}"

fi

exit $bazel_status
