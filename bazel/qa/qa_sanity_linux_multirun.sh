#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Invokes UI tests with a large value for runs_per_test. Expected to take a long
# time to finish.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invalidate local cache to avoid picking up obsolete test result xmls
"${script_dir}/../bazel" clean --async

config_options="--config=remote"
runs_per_test=1000

readonly invocation_id_sanity_longrunning="$(uuidgen)"

target_filters=qa_sanity,ui_psq,-qa_unreliable,-no_linux,-no_test_linux,-requires_emulator
"${script_dir}/../bazel" \
  --max_idle_secs=60 \
  test \
  --runs_per_test=${runs_per_test} \
  --runs_per_test_detects_flakes \
  --jobs=200 \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id_sanity_longrunning} \
  --define=meta_android_build_number=${build_number} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  --tool_tag=${script_name} \
  -- \
  //tools/adt/idea/android-uitests:AddKotlinTest \
  //tools/adt/idea/android-uitests:AddNewBuildTypeTest \
  //tools/adt/idea/android-uitests:AddRemoveCppDependencyTest \
  //tools/adt/idea/android-uitests:BasicLayoutEditTest \
  //tools/adt/idea/android-uitests:CMakeListsTest \
  //tools/adt/idea/android-uitests:CompileWithJava8Test \
  //tools/adt/idea/android-uitests:ConvertFromWebpToPngTest \
  //tools/adt/idea/android-uitests:CreateCppKotlinProjectTest \
  //tools/adt/idea/android-uitests:CreateDefaultActivityTest \
  //tools/adt/idea/android-uitests:CreateNavGraphTest \
  //tools/adt/idea/android-uitests:CreateNewAppModuleWithDefaultsTest \
  //tools/adt/idea/android-uitests:CreateNewFlavorsTest \
  //tools/adt/idea/android-uitests:CreateNewMobileProjectTest \
  //tools/adt/idea/android-uitests:CreateNewProjectWithCpp1Test \
  //tools/adt/idea/android-uitests:CreateNewProjectWithCpp2Test \
  //tools/adt/idea/android-uitests:CreateNewProjectWithCpp3Test \
  //tools/adt/idea/android-uitests:GenerateApkWithReleaseVariantTest \
  //tools/adt/idea/android-uitests:ModifyMinSdkAndSyncTest \
  //tools/adt/idea/android-uitests:NewComposeProjectTest \
  //tools/adt/idea/android-uitests:OpenCloseVisualizationToolTest \
  //tools/adt/idea/android-uitests:OpenExistingProjectTest \
  //tools/adt/idea/android-uitests:QuickFixForJniTest \
  //tools/adt/idea/android-uitests:RunOnEmulatorTest

readonly bazel_status_sanity_longrunning=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id_sanity_longrunning}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly testlogs_dir="$("${script_dir}/../bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/testlogs

  # aggregate test results into a single XML
  ("${script_dir}"/utils/aggregate_xmls.py --testlogs_dir="${testlogs_dir}" --output_file="${dist_dir}"/testlogs/aggregated_results.xml)
fi

# See https://docs.bazel.build/versions/master/guide.html#what-exit-code-will-i-get
# Exit code 0: successful test run
# Exit code 3: tests failed or timed out, ignore for manual review
# Exit code 4: No tests found, check filters (maybe)
case $bazel_status_sanity_longrunning in
  [034])
    exit 0
    ;;
  *)
    exit $bazel_status_sanity_longrunning
    ;;
esac

exit 0
