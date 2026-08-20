{
  mkGradleBuild,
  build-logic,
  deps-compile,
  common-compile,
}:
mkGradleBuild {
  pname = "ai-plugin";
  module = "ai";
  versionProperty = "plugin.ai.version";
  srcRoots = [
    ../../../build-logic
    ../../../deps
    ../../../common
    ../../../ai
    ./ai.nix
  ];
  task = ":ai:distZip";
  buildDependencies = [
    build-logic
    deps-compile
    common-compile
  ];
  installPhase = ''
    mkdir -p $out
    cp ai/build/distributions/ai-''$version.zip $out/
  '';
}
