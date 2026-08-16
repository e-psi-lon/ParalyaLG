{
  lib,
  stdenv,
  dockerTools,
  cacert,
  writeTextDir,
  project-jdk,
  lastCommitAsTimestamp,
  paralyabot-jar,
}:
let
  headlessJdk = project-jdk.override {
    headless = true;
    enableGtk = false;
    enableJavaFX = false;
  };
  project-jre = stdenv.mkDerivation {
    pname = "${headlessJdk.pname}-minimal-jre";
    version = headlessJdk.version;

    nativeBuildInputs = [ headlessJdk ];
    strictDeps = true;
    dontUnpack = true;
    dontInstall = true;
    stripDebugFlags = [ "--strip-unneeded" ];

    buildPhase = ''
      jlink --module-path ${headlessJdk}/lib/openjdk/jmods \
        --add-modules java.base,java.xml,java.naming \
        --no-header-files --no-man-pages --strip-debug \
        --compress=2 \
        --output $out
    '';
    inherit (headlessJdk) meta;
  };
in
dockerTools.streamLayeredImage {
  name = "paralyabot";
  tag = "latest";
  created = lastCommitAsTimestamp;

  contents =
    let
      nixosVersion = lib.trivial.release;
      nixosCodeName = lib.trivial.codeName;

      nixosCodeNamePretty =
        let
          firstChar = lib.toUpper (builtins.substring 0 1 nixosCodeName);
          restChars = builtins.substring 1 (builtins.stringLength nixosCodeName) nixosCodeName;
        in
        "${firstChar}${restChars}";

      prettyName = "${nixosVersion} (${nixosCodeNamePretty})";
      versionSuffix = lib.trivial.versionSuffix;

      osRelease = {
        NAME = "Nix Container";
        ID = "nix-container";
        VERSION = prettyName;
        VERSION_ID = nixosVersion;
        VERSION_CODENAME = nixosCodeName;
        PRETTY_NAME = "Nix Container ${prettyName}";
        BUILD_ID = "${nixosVersion}.${versionSuffix}";
        CPE_NAME = "";
        DEFAULT_HOSTNAME = "nixos";
        LOGO = "nix-snowflake";
        HOME_URL = "https://nixos.org/manual/nix/stable/";
        DOCUMENTATION_URL = "https://nix.dev";
        SUPPORT_URL = "https://nixos.org/community.html";
        BUG_REPORT_URL = "https://github.com/NixOS/nixpkgs/issues";
        VENDOR_NAME = "NixOS";
        VENDOR_URL = "https://nixos.org/";
        ANSI_COLOR = "0;38;2;126;186;228";
        ID_LIKE = "nixos";
        IMAGE_ID = "paralyabot";
        IMAGE_VERSION = "${paralyabot-jar.version}";
        VARIANT = "Container";
        VARIANT_ID = "container";
      };

      lsbRelease = {
        DISTRIB_ID = "nixos";
        DISTRIB_RELEASE = nixosVersion;
        DISTRIB_CODENAME = nixosCodeName;
        DISTRIB_DESCRIPTION = "Nix Container ${prettyName}";
        LSB_VERSION = prettyName;
      };
      toEnvFile = attrs: builtins.concatStringsSep "\n" (
        lib.mapAttrsToList (k: v: "${k}=${toString v}") attrs
      );
    in
    [
      cacert
      (writeTextDir "etc/os-release" (toEnvFile osRelease))
      (writeTextDir "etc/lsb-release" (toEnvFile lsbRelease))
    ];

  extraCommands = ''
    mkdir -p app tmp
    chmod 1777 tmp
  '';

  config = {
    User = "1000:1000";
    Entrypoint = [
      (lib.getExe project-jre)
      "-XX:+UseCompactObjectHeaders"
      "-XX:+UseContainerSupport"
      "-XX:+UseStringDeduplication"
      "-XX:+PerfDisableSharedMem"
      "-XX:SoftMaxHeapSize=170m"
      "-XX:+DisableExplicitGC"
      "-XX:+ExitOnOutOfMemoryError"
      "-XX:MaxGCPauseMillis=50"
      "-XX:G1HeapRegionSize=1m"
      "-XX:ReservedCodeCacheSize=64m"
      "-Xms128m"
      "-Xmx256m"
      "--enable-native-access=ALL-UNNAMED"
      "-jar"
      "${paralyabot-jar}/paralya-bot.jar"
    ];
    WorkingDir = "/app";
    Env = [
      "PARALYA_BOT_CONFIG_FILE=/app/external/config.conf"
      "PARALYA_BOT_PLUGINS_DIR=/app/external/plugins"
      "BOT_DEVELOPER_ID=708006478807695450"
      # Required by data collection even if disabled
      "DATA_COLLECTION_UUID=7f7bec74-d7f9-4320-9a7e-46eab2be31c8"
    ];
    Volumes = {
      "/app/external" = { };
    };
  };

  maxLayers = 25;
}
