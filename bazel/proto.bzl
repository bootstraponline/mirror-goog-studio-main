# Enum-like values to determine the language the gen_proto rule will compile
# the .proto files to.
proto_languages = struct(
  CPP = 0,
  JAVA = 1
)

def _gen_proto_impl(ctx):
  gen_dir = ctx.label.package
  inputs = []
  inputs += ctx.files.srcs
  args = ["--proto_path=" + gen_dir] + [s.path for s in inputs]

  # Try to generate cc protos first.
  if ctx.attr.target_language == proto_languages.CPP:
    out_path = ctx.var["GENDIR"] + "/" + gen_dir
    args += [
        "--cpp_out=" + out_path,
        "--grpc_out=" + out_path,
        "--plugin=protoc-gen-grpc=" + ctx.executable.grpc_plugin.path
    ]

    outs = ctx.outputs.outs

  # Try to generate java protos only if we won't generate cc protos
  elif ctx.attr.target_language == proto_languages.JAVA:
    srcjar = ctx.outputs.outs[0] # outputs.out size should be 1
    outs = [ctx.new_file(srcjar.basename + ".jar")]

    out_path = outs[0].path
    args += [
        "--java_out=" + out_path,
        "--java_rpc_out=" + out_path,
        "--plugin=protoc-gen-java_rpc=" + ctx.executable.grpc_plugin.path
    ]

    inputs += [ctx.executable.grpc_plugin]

  ctx.action(
      mnemonic = "GenProto",
      inputs = inputs,
      outputs = outs,
      arguments = args,
      executable = ctx.executable.protoc,
  )

  if ctx.attr.target_language == proto_languages.JAVA:
    # This is required because protoc only understands .jar extensions, but Bazel
    # requires source JAR files end in .srcjar.
    ctx.action(
        mnemonic = "FixProtoSrcJar",
        inputs = outs,
        outputs = [srcjar],
        arguments = [srcjar.path + ".jar", srcjar.path],
        command = "cp $1 $2"
    )

_gen_proto_rule = rule(
  attrs = {
      "srcs": attr.label_list(
          allow_files = FileType([".proto"]),
      ),
      "deps": attr.label_list(
          allow_files = False,
          providers = ["proto_src"],
      ),
      "protoc": attr.label(
          cfg = "host",
          executable = True,
          mandatory = True,
          single_file = True,
      ),
      "grpc_plugin": attr.label(
          cfg = "host",
          executable = True,
          mandatory = True,
          single_file = True,
      ),
      "target_language": attr.int(),
      "outs": attr.output_list(),
  },
  output_to_genfiles = True,
  implementation = _gen_proto_impl,
)

def java_proto_library(name, srcs=None, deps=[], visibility=None):
  srcs_name = name + "_srcs"
  outs = [srcs_name + ".srcjar"]
  _gen_proto_rule(
      name = srcs_name,
      srcs = srcs,
      deps = deps,
      outs = outs,
      protoc = "//prebuilts/tools/common/m2/repository/com/google/protobuf/protoc/3.0.0-beta-2:exe",
      grpc_plugin = "//prebuilts/tools/common/m2/repository/io/grpc/protoc-gen-grpc-java/0.13.2:exe",
      target_language = proto_languages.JAVA
  )

  native.java_library(
      name  = name,
      srcs = outs,
      deps = [
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.0.0-beta-2:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-all/0.13.2:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/18.0:jar",
      ],
      visibility = visibility,
  )

def cc_grpc_proto_library(name, srcs=[], deps=[], includes=[], visibility=None):
  outs = []
  for src in srcs:
    # .proto suffix should not be present in the output files
    p_name = src[:-len(".proto")]
    outs += [p_name + ".pb.h", p_name + ".pb.cc", p_name + ".grpc.pb.h", p_name + ".grpc.pb.cc"]

  _gen_proto_rule(
      name = name + "_srcs",
      srcs = srcs,
      deps = deps,
      outs = outs,
      protoc = "//external:protoc",
      grpc_plugin = "//external:grpc_cpp_plugin",
      target_language = proto_languages.CPP
  )

  native.cc_library(
    name = name,
    srcs = outs,
    deps = deps + ["//external:grpc++_unsecure"],
    includes = includes,
    visibility = visibility,
  )

