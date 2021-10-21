#!/bin/bash
startTime=$(date +'%Y-%m-%d %H:%M:%S')

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

ARCS=(
  "arm"
  "arm64"
  "x86"
  "x86_64"
)

for arc in "${ARCS[@]}" 
do
  "$DIR/build.ffmpeg.sh" "$arc"
done

endTime=$(date +'%Y-%m-%d %H:%M:%S')
startSeconds=$(date --date="$startTime" +%s)
endSeconds=$(date --date="$endTime" +%s)
echo "build finish in "$((endSeconds-startSeconds))"s"