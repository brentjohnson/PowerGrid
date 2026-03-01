#!/bin/sh
printf '\033c\033]0;%s\a' PowerGrid
base_path="$(dirname "$(realpath "$0")")"
"$base_path/PowerGrid.x86_64" "$@"
