{
  lib,
  writeShellScriptBin,
  mkShell,
  project-jdk,
  extractVersion,
}:
let
  build-bot = writeShellScriptBin "build-bot" ''
    set -e
    IMAGE_PATH=$(nix build .#paralyabot-image --print-out-paths --show-trace)

    RUNTIME=""
    if command -v docker &> /dev/null; then
        RUNTIME="docker"
    elif command -v podman &> /dev/null; then
        RUNTIME="podman"
    else
        echo "Neither docker nor podman found"
        exit 1
    fi

    echo "Loading image into $RUNTIME..."
    "$IMAGE_PATH" | "$RUNTIME" load
  '';

  run-bot = writeShellScriptBin "run-bot" ''
    CONTAINER_NAME="''${1:-ParalyaBot}"
    echo "Starting container: $CONTAINER_NAME..."
    podman run \
        --name "$CONTAINER_NAME" \
        --replace \
        --detach \
        --userns=keep-id \
        --volume "$PWD/container:/app/external:Z" \
        localhost/paralyabot:latest
    echo "Container $CONTAINER_NAME started successfully."
  '';

  build-and-run-bot = writeShellScriptBin "build-and-run-bot" ''
    ${lib.getExe build-bot} && ${lib.getExe run-bot} ''${1:-ParalyaBot}
  '';

  build-plugin = writeShellScriptBin "build-plugin" ''
    PLUGIN=$1
    KEEP_RESULT=''${2:-false}

    if [ -z "$PLUGIN" ]; then
        echo "Usage: build-plugin <plugin-name> [keep-result]"
        exit 1
    fi

    NO_LINK=""
    if [ "$KEEP_RESULT" = "false" ]; then
        NO_LINK="--no-link"
    fi

    nix build .#$PLUGIN-plugin \
        --print-out-paths \
        $NO_LINK
  '';

  deploy-plugin = writeShellScriptBin "deploy-plugin" ''
    PLUGIN=$1
    if [ -z "$PLUGIN" ]; then
        echo "Usage: deploy-plugin <plugin-name>"
        exit 1
    fi

    echo "Building $PLUGIN-plugin..."
    OUT_PATH=$(${lib.getExe build-plugin} "$PLUGIN" false)

    PLUGIN_DIR="$PWD/container/plugins"
    mkdir -p "$PLUGIN_DIR"

    cp -f "$OUT_PATH"/*.zip "$PLUGIN_DIR/"

    for zip in "$OUT_PATH"/*.zip; do
        FILENAME=$(basename "$zip")
        DIRNAME="''${FILENAME%.zip}"
        if [ -d "$PLUGIN_DIR/$DIRNAME" ]; then
            rm -rf "$PLUGIN_DIR/$DIRNAME"
        fi
    done

    echo "$PLUGIN deployed successfully."
  '';

  update-deps = writeShellScriptBin "update-deps" ''
    set -e
    case "''${1:-}" in
        --help|-h)
            echo "Usage: update-deps [PACKAGE]"
            echo "If no package is specified, updates all dependency lockfiles."
            ;;
        "")
            for pkg_update in build-logic-update deps-compile-update common-update paralyabot-jar-update lg-plugin-update sta-plugin-update ai-plugin-update; do
                if ! $(nix build .#''${pkg_update} --print-out-paths); then
                    echo "Error: Failed to update ''${pkg_update} dependencies. Check the output above for details." >&2
                fi
            done
            ;;
        *)
            if ! $(nix build .#"''${1}-update" --print-out-paths); then
                 echo "Error: Failed to update ''${1} dependencies. Check the output above for details." >&2
            fi
            ;;
    esac
  '';

  kordex-update = writeShellScriptBin "kordex-update" ''
    ${builtins.readFile ./kordex-update.sh}
  '';
in
mkShell {
  shellHook = ''
    ln -sfn ${project-jdk}/lib/openjdk .jdk
    export JAVA_HOME=$PWD/.jdk
    export PARALYABOT_VERSION=${extractVersion "paralyabot.version"}
  '';
  packages = [
    project-jdk
    build-bot
    run-bot
    build-and-run-bot
    build-plugin
    deploy-plugin
    update-deps
    kordex-update
  ];
}
