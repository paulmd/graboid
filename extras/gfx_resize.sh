#!/bin/bash

# Copyright (c) 2013 Paul Muad'Dib
#
# This file is part of Graboid.
# 
# Graboid is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Graboid is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Graboid.  If not, see <http://www.gnu.org/licenses/>.

EXTRAS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

DEPLOY_FILES=( nokey.png clean.png loaded.png recording.png replaying.png )
for f in "${DEPLOY_FILES[@]}"
do
  echo Deploying gfx file: $f
	cp ${EXTRAS_DIR}/$f ${EXTRAS_DIR}/../app/res/drawable-mdpi/$f
	convert ${EXTRAS_DIR}/$f -resize 75% ${EXTRAS_DIR}/../app/res/drawable-ldpi/$f
	convert ${EXTRAS_DIR}/$f -resize 150% ${EXTRAS_DIR}/../app/res/drawable-hdpi/$f
  convert ${EXTRAS_DIR}/$f -resize 200% ${EXTRAS_DIR}/../app/res/drawable-xhdpi/$f
done

echo Deploying gfx file: icon.png
convert ${EXTRAS_DIR}/icon.png -resize 48 ${EXTRAS_DIR}/../app/res/drawable-mdpi/ic_launcher.png
convert ${EXTRAS_DIR}/icon.png -resize 36 ${EXTRAS_DIR}/../app/res/drawable-ldpi/ic_launcher.png
convert ${EXTRAS_DIR}/icon.png -resize 72 ${EXTRAS_DIR}/../app/res/drawable-hdpi/ic_launcher.png
convert ${EXTRAS_DIR}/icon.png -resize 96 ${EXTRAS_DIR}/../app/res/drawable-xhdpi/ic_launcher.png

