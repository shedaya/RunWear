#!/bin/bash
# Local Gradle route for this worktree (AGENTS.md lessons):
# private net namespace + IPv6 off + warmed cache + explicit GRADLE_USER_HOME
# (Java resolves user.home to /root under the namespace; the warmed cache
# lives under /home/hatch/.gradle).
exec unshare -Urmn bash -c '
ip link set lo up 2>/dev/null
sysctl -w net.ipv6.conf.all.disable_ipv6=1 net.ipv6.conf.default.disable_ipv6=1 net.ipv6.conf.lo.disable_ipv6=1 >/dev/null 2>&1
export JAVA_HOME=$HOME/workspace/.jdks/jdk-17.0.20.1+1
export PATH=$JAVA_HOME/bin:$PATH
export GRADLE_USER_HOME=/home/hatch/.gradle
export ANDROID_HOME=$HOME/workspace/.android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
cd ~/workspace/runwear-gen6-wavea-android-wt/RunWear-android
exec ~/workspace/.gradle-dists/gradle-8.9/bin/gradle "$@" --offline
' _ "$@"
