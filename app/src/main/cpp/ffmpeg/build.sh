#!/bin/bash

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

ARCS=(
  "arm"
  "arm64"
  "x86"
  "x86_64"
  "os"
)

for arc in "${ARCS[@]}" 
do
  startTime=$(date +'%Y-%m-%d %H:%M:%S')
  if [ "$arc" == "os" ]; then
    wsl -e "$DIR/build.ffmpeg.sh" "$arc"
  else
    "$DIR/build.ffmpeg.sh" "$arc"
  fi
  endTime=$(date +'%Y-%m-%d %H:%M:%S')
  startSeconds=$(date --date="$startTime" +%s)
  endSeconds=$(date --date="$endTime" +%s)
  echo "build $arc finish in $((endSeconds-startSeconds))s"
done

