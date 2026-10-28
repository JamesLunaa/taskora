#!/usr/bin/env bash
# Builds the Linux packages from the app image that Compose's createDistributable makes:
#   ./packaging/build-packages.sh [appimage] [deb] [rpm]    (default: all three)
# Output goes to build/packages/. The two packaging tools (appimagetool, nfpm) are taken from
# PATH, or downloaded once into build/tools/ at pinned versions and checked against their SHA-256.
# Arch Linux uses packaging/arch/PKGBUILD instead.
set -euo pipefail

cd "$(dirname "$0")/.."
DESKTOP_DIR=$PWD

APPIMAGETOOL_VERSION=1.9.1
APPIMAGETOOL_SHA256=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
NFPM_VERSION=2.48.0
NFPM_SHA256=6d7f9a28b2f397934403950fcbba83f598b08d02edbe79a0815b3b4897341750

APP_ID=io.github.jameslunaa.taskora
TOOLS=$DESKTOP_DIR/build/tools
OUT=$DESKTOP_DIR/build/packages
APP_IMAGE=$DESKTOP_DIR/build/compose/binaries/main/app/taskora

formats=("$@")
[ ${#formats[@]} -eq 0 ] && formats=(appimage deb rpm)

[ "$(uname -m)" = x86_64 ] || { echo "Only x86_64 is supported for now." >&2; exit 1; }

# version = "x.y.z" in build.gradle.kts.
VERSION=$(sed -n 's/^version = "\(.*\)"$/\1/p' build.gradle.kts)
export VERSION

download() { # url sha256 file
    mkdir -p "$TOOLS"
    if [ ! -f "$3" ]; then
        echo "Downloading $1"
        curl -sSfL -o "$3.part" "$1"
        mv "$3.part" "$3"
    fi
    echo "$2  $3" | sha256sum --check --quiet || { rm -f "$3"; echo "Checksum mismatch: $1" >&2; exit 1; }
}

appimagetool() {
    if command -v appimagetool >/dev/null; then
        command appimagetool "$@"
        return
    fi
    local tool=$TOOLS/appimagetool-$APPIMAGETOOL_VERSION.AppImage
    download "https://github.com/AppImage/appimagetool/releases/download/$APPIMAGETOOL_VERSION/appimagetool-x86_64.AppImage" \
        "$APPIMAGETOOL_SHA256" "$tool"
    chmod +x "$tool"
    # Runs without FUSE (e.g. in containers) by unpacking itself.
    APPIMAGE_EXTRACT_AND_RUN=1 "$tool" "$@"
}

nfpm() {
    if command -v nfpm >/dev/null; then
        command nfpm "$@"
        return
    fi
    local archive=$TOOLS/nfpm_${NFPM_VERSION}_Linux_x86_64.tar.gz
    download "https://github.com/goreleaser/nfpm/releases/download/v$NFPM_VERSION/nfpm_${NFPM_VERSION}_Linux_x86_64.tar.gz" \
        "$NFPM_SHA256" "$archive"
    [ -x "$TOOLS/nfpm" ] || tar -xzf "$archive" -C "$TOOLS" nfpm
    "$TOOLS/nfpm" "$@"
}

build_appimage() {
    local appdir=$DESKTOP_DIR/build/appimage/Taskora.AppDir
    rm -rf "$appdir"
    mkdir -p "$appdir/usr/lib" "$appdir/usr/share/applications" "$appdir/usr/share/metainfo"
    cp -a "$APP_IMAGE" "$appdir/usr/lib/taskora"
    ln -s usr/lib/taskora/bin/taskora "$appdir/AppRun"
    cp packaging/$APP_ID.desktop "$appdir/"
    cp packaging/$APP_ID.desktop "$appdir/usr/share/applications/"
    # appimagetool looks for <id>.appdata.xml.
    cp packaging/$APP_ID.metainfo.xml "$appdir/usr/share/metainfo/$APP_ID.appdata.xml"
    for icon in packaging/icons/*/; do
        size=$(basename "$icon")
        mkdir -p "$appdir/usr/share/icons/hicolor/$size/apps"
        cp "$icon$APP_ID.png" "$appdir/usr/share/icons/hicolor/$size/apps/"
    done
    cp packaging/icons/256x256/$APP_ID.png "$appdir/$APP_ID.png"
    ln -s $APP_ID.png "$appdir/.DirIcon"
    mkdir -p "$OUT"
    ARCH=x86_64 appimagetool --no-appstream "$appdir" "$OUT/Taskora-$VERSION-x86_64.AppImage"
}

build_nfpm() { # deb | rpm
    mkdir -p "$OUT"
    nfpm package --config packaging/nfpm.yaml --packager "$1" --target "$OUT/"
}

./gradlew --quiet createDistributable

for format in "${formats[@]}"; do
    case $format in
        appimage) build_appimage ;;
        deb | rpm) build_nfpm "$format" ;;
        *) echo "Unknown format: $format (use appimage, deb or rpm)" >&2; exit 1 ;;
    esac
done
ls -lh "$OUT"
