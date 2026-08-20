{
  description = "The official flake for ParalyaBot, the Discord bot of the Paralya server.";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/release-25.11";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      lib = pkgs.lib;
      project-jdk = pkgs.jdk25;

      baseGradleFileset = lib.fileset.unions [
        ./build-logic
        ./settings.gradle.kts
        ./gradle.properties
        ./gradle
        ./flake.nix
        ./flake.lock
        ./config
        ./nix/parse-properties.nix
        ./build.gradle.kts
      ];

      modules = [
        "build-logic"
        "deps"
        "common"
        "bot"
        "lg"
        "sta"
        "ai"
      ];

      utils = import ./nix/parse-properties.nix {
        inherit lib;
        inherit (self) lastModifiedDate;
      };
      mkGradleBuild = import ./nix/gradle.nix {
        inherit
          lib
          baseGradleFileset
          modules
          project-jdk
          ;
        inherit (pkgs) stdenv gradle-packages;
        inherit (utils) parseProperties extractVersion;
      };

    in
    {
      packages.${system} =
        let
          build-logic = import ./nix/packages/build-logic.nix { inherit mkGradleBuild; };
          deps = import ./nix/packages/deps.nix {
            inherit (pkgs) runCommand;
            inherit mkGradleBuild build-logic;
          };
          common = import ./nix/packages/common.nix {
            inherit (pkgs) runCommand;
            inherit mkGradleBuild build-logic;
            inherit (utils) extractVersion;
            inherit (deps) deps-compile deps-runtime;
          };
          paralyabot = import ./nix/packages/paralyabot.nix {
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile;
            inherit (common) common-compile common-runtime;
          };
          paralyabot-image = import ./nix/packages/image.nix {
            inherit lib project-jdk;
            inherit (pkgs) dockerTools cacert writeTextDir stdenv;
            inherit (paralyabot) paralyabot-jar;
            inherit (utils) lastCommitAsTimestamp;
          };
          lg-plugin = import ./nix/packages/plugins/lg.nix {
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile;
            inherit (common) common-compile;
          };
          ai-plugin = import ./nix/packages/plugins/ai.nix {
            inherit mkGradleBuild build-logic;
            inherit (deps) deps-compile;
            inherit (common) common-compile;
          };
        in
        {
          inherit build-logic paralyabot-image lg-plugin ai-plugin;
          inherit (deps) deps-compile deps-runtime;
          inherit (common) common-compile common-runtime-deps common-runtime common-update;
          inherit (paralyabot) paralyabot-jar paralyabot-jar-deps paralyabot-jar-update;

          build-logic-update = build-logic.mitmCache.updateScript;
          deps-compile-update = deps.deps-compile.mitmCache.updateScript;
          lg-plugin-update = lg-plugin.mitmCache.updateScript;
          ai-plugin-update = ai-plugin.mitmCache.updateScript;
        };

      devShells.${system}.default = import ./nix/shell.nix {
        inherit lib project-jdk;
        inherit (pkgs) writeShellScriptBin mkShell;
        inherit (utils) extractVersion;
      };
    };
}
