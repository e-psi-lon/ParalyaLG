{
  gradle-packages,
  stdenv,
  lib,
  project-jdk,
  baseGradleFileset,
  modules,
  parseProperties,
  extractVersion,
}:
let
  wrapperProperties = parseProperties ../gradle/wrapper/gradle-wrapper.properties;
  gradleVersion =
    let
      url = wrapperProperties.distributionUrl;
      match = builtins.match ".*gradle-([0-9.]+)-bin\\.zip" url;
    in
    builtins.elemAt match 0;
  gradle-wrapper =
    (gradle-packages.mkGradle {
      version = gradleVersion;
      hash = "sha256-KrKVjyoeURIMMmytbzhRU7sR7pOzwhbF/M6/37t+xss=";
      defaultJava = project-jdk;
    }).wrapped;
in
{
  pname,
  module ? pname,
  versionProperty ? "paralyabot.version",
  artifactVersion ? extractVersion versionProperty,
  task,
  updateTask ? ":${module}:nixDownloadDepsFixed",
  srcRoots ? [ ],
  depsData ? ../${module}/deps.json,
  buildDependencies ? [ ],
  installPhase,
  preBuild ? "",
  extraNativeInputs ? [ ],
  extraGradleFlags ? [ ],
}:
let
  version = extractVersion versionProperty;
  dependencyFlags =
    let
      validDeps = builtins.filter (dep: dep ? passthru && dep.passthru ? gradleProperties) buildDependencies;
      extractFlags = dep: lib.mapAttrsToList (key: val: "-P${key}=${val}") dep.passthru.gradleProperties;
    in
    lib.flatten (map extractFlags validDeps);
in
stdenv.mkDerivation (finalAttrs: {
  inherit pname;
  version = artifactVersion;
  src = lib.fileset.toSource {
    root = ../.;
    fileset = lib.fileset.unions ([ baseGradleFileset ] ++ srcRoots ++ [ ./gradle.nix ]);
  };
  nativeBuildInputs = [ gradle-wrapper ] ++ extraNativeInputs;

  strictDeps = true;
  mitmCache = gradle-wrapper.fetchDeps {
    pkg = finalAttrs.finalPackage;
    pname = pname;
    data = depsData;
  };

  gradleBuildTask = task;
  gradleUpdateTask = updateTask;
  gradleFlags = [ "-P${versionProperty}=${version}" ] ++ dependencyFlags ++ extraGradleFlags;

  preBuild = ''
    mkdir -p $GRADLE_USER_HOME
    ${lib.concatMapStrings (module: ''
      if [ ! -f "${module}/build.gradle.kts" ]; then
        mkdir -p "${module}"
        touch "${module}/build.gradle.kts"
      fi
    '') modules}

    ${lib.concatMapStrings (dep: ''
      mkdir -p ${dep.passthru.buildDir}/build/
      mkdir -p $GRADLE_USER_HOME/caches/
      cp -rL --no-preserve=mode ${dep}/${dep.passthru.buildDir}/build/. ${dep.passthru.buildDir}/build/
      cp -rL --no-preserve=mode ${dep}/gradle-home/caches/. $GRADLE_USER_HOME/caches/
    '') buildDependencies}

    chmod -R u+w .
    chmod -R u+w $GRADLE_USER_HOME

    ${preBuild}
  '';

  passthru = {
    buildDir = module;
    gradleProperties."${versionProperty}" = version;
  };

  env = {
    GRADLE_USER_HOME = "./gradle-home";
    JAVA_HOME = "${project-jdk}";
    LC_ALL = "C.UTF-8";
    SOURCE_DATE_EPOCH = "1730143059";
    JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF8 -Djava.properties.date=2024-10-28T19:17:39Z";
  };

  installPhase = ''
    export version=${version}
    ${installPhase}
  '';
})
