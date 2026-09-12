{
  runCommand,
  mkGradleBuild,
  extractVersion,
  build-logic,
  deps-compile,
  deps-runtime,
}:
let
  sharedArgs = {
    module = "common";
    buildDependencies = [
      build-logic
      deps-compile
    ];
    extraGradleFlags = [
      "-Pmodule.common.min-compatible-version=${extractVersion "module.common.min-compatible-version"}"
    ];
  };

  commonArgs = sharedArgs // {
    srcRoots = [
      ../../build-logic
      ../../deps
      ../../common
      ./common.nix
    ];
    task = "common:jar";
    updateTask = "common:nixDownloadDepsFixed";
  };

  common-runtime-deps = mkGradleBuild (sharedArgs // {
    pname = "paralyabot-common-deps";
    artifactVersion = "static";
    versionProperty = "module.common.deps.version";
    srcRoots = [
      ../../common/build.gradle.kts
      ./common.nix
    ];
    task = "common:copyRuntimeClasspath";
    installPhase = ''
      mkdir -p $out
      cp common/build/deps/*.jar $out/
    '';
  });

  common-update = (mkGradleBuild (commonArgs // {
    "pname" = "paralyabot-common-update";
    preBuild = ''
      CLASS_PATH=${deps-runtime}/paralya-bot-deps.jar
      export RAW_CLASSPATH="$CLASS_PATH $EXTRA_JARS"
    '';
    installPhase = '''';
  })).mitmCache.updateScript;

  common-compile-drv = mkGradleBuild (commonArgs // {
    pname = "paralyabot-common-compile";
    versionProperty = "module.common.version";
    preBuild = ''
      CLASS_PATH=${deps-runtime}/paralya-bot-deps.jar
      EXTRA_JARS=$(ls ${common-runtime-deps}/*.jar | tr '\n' ' ')
      export RAW_CLASSPATH="$CLASS_PATH $EXTRA_JARS"
    '';
    installPhase = ''
      mkdir -p $out/common/build $out/gradle-home
      cp -r common/build/. $out/common/build/
      cp -r $GRADLE_USER_HOME/caches $out/gradle-home/caches
      cp common/build/libs/paralya-bot-common.jar $out/
    '';
  });

  common-compile = common-compile-drv // {
    passthru = (common-compile-drv.passthru or {}) // {
      gradleProperties = (common-compile-drv.passthru.gradleProperties or {}) // {
        "module.common.min-compatible-version" = extractVersion "module.common.min-compatible-version";
      };
    };
  };

  common-runtime =
    runCommand "paralyabot-common-runtime" {}
      ''
        mkdir -p $out META-INF
        cp --no-preserve=mode ${common-compile}/paralya-bot-common.jar $out/
        mkdir -p $out/nix-support
        echo ${deps-runtime} > $out/nix-support/runtime-depends
        echo ${common-runtime-deps} >> $out/nix-support/runtime-depends
      '';
in {
  inherit common-compile common-runtime-deps common-runtime common-update;
}
