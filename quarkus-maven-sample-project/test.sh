#!/usr/bin/env bash

function assertNoConfigDump() {
    local buildLog=$1
    local quarkusPropertiesNotFound=$(grep "Quarkus previous properties not found" ${buildLog} | wc -l)

    if [ "${quarkusPropertiesNotFound}" == "0" ]
    then
        echo "ERROR - config dump is present"
        exit 1
    fi
}

function assertCacheHitCount() {
    local buildLog=$1
    local count=$2
    local cacheHit=$(grep "Configuring caching for Quarkus build" -A 1 ${buildLog} | grep "Loaded from the build cache" | wc -l)

    if [ "${cacheHit}" != "${count}" ]
    then
        echo "ERROR - cache hit = ${cacheHit}"
        exit 1
    fi
}

function assertCacheMiss() {
    assertCacheHitCount $1 0
}

function assertCacheHit() {
    assertCacheHitCount $1 1
}

function assertPropertyChanged() {
    local buildLog=$1
    local quarkusProp=$2
    local quarkusPropChanged=$(grep -E "Quarkus properties have changed.*${quarkusProp}" ${buildLog} | wc -l)

    if [ "$quarkusPropChanged" != "1" ]
    then
        echo "ERROR - property change not detected"
        exit 1
    fi
}

# Clean local cache
rm -rf ~/.m2/.gradle-enterprise/build-cache

# Clean quarkus dump
rm -rf .quarkus/*-config-dump

# Clean log files
rm -f /tmp/build*.log

# Run build 1
echo "Run build 1 - Expect no config dump"
buildLog="/tmp/build1.log"
./mvnw -B clean package -DskipTests -Dscan.tag.build1 2>&1 | tee -a ${buildLog}
assertNoConfigDump ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check1

# Run build 2
echo "Run build 2 - Expect cache miss"
buildLog="/tmp/build1.log"
./mvnw -B clean package -DskipTests -Dscan.tag.build2 2>&1 | tee -a ${buildLog}
assertCacheMiss ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check2

# Run build 3
echo "Run build 3 - Expect cache hit"
buildLog="/tmp/build3.log"
./mvnw -B clean package -DskipTests -Dscan.tag.build3 2>&1 | tee -a ${buildLog}
assertCacheHit ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check3

# Run build 4
echo "Run build 4 - Expect property changed"
buildLog="/tmp/build4.log"
quarkusProperty="quarkus.live-reload.retry-interval"
./mvnw -B clean package -DskipTests -D${quarkusProperty}=15s -Dscan.tag.build4 2>&1 | tee -a ${buildLog}
assertPropertyChanged ${buildLog} ${quarkusProperty}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check4

# Run build 5
echo "Run build 5 - Expect cache miss"
buildLog="/tmp/build5.log"
./mvnw -B clean package -DskipTests -D${quarkusProperty}=15s -Dscan.tag.build5 2>&1 | tee -a ${buildLog}
assertCacheMiss ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check5

# Run build 6
echo "Run build 6 - Expect cache hit"
buildLog="/tmp/build6.log"
./mvnw -B clean package -DskipTests -D${quarkusProperty}=15s -Dscan.tag.build6 2>&1 | tee -a ${buildLog}
assertCacheHit ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check6

# Run build 7
echo "Run build 7 - Expect property changed"
buildLog="/tmp/build7.log"
./mvnw -B clean package -DskipTests -Dnative -D${quarkusProperty}=15s -Dscan.tag.build7 2>&1 | tee -a ${buildLog}
assertPropertyChanged ${buildLog} "quarkus.package.type"
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check7

# Run build 8
echo "Run build 8 - Expect cache miss"
buildLog="/tmp/build8.log"
./mvnw -B clean package -DskipTests -Dnative -D${quarkusProperty}=15s -Dscan.tag.build8 2>&1 | tee -a ${buildLog}
assertCacheMiss ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check8

# Run build 9
echo "Run build 9 - Expect cache hit"
buildLog="/tmp/build9.log"
./mvnw -B clean package -DskipTests -Dnative -D${quarkusProperty}=15s -Dscan.tag.build9 2>&1 | tee -a ${buildLog}
assertCacheHit ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check9

# Run build 10
rm -rf .quarkus/*-config-dump
echo "Run build 10 - Expect no config dump"
buildLog="/tmp/build10.log"
./mvnw -B clean package -DskipTests -Dnative -Dscan.tag.build10 2>&1 | tee -a ${buildLog}
assertNoConfigDump ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check10

# Run build 11
echo "Run build 11 - Expect cache miss"
buildLog="/tmp/build11.log"
./mvnw -B clean package -DskipTests -Dnative -Dscan.tag.build11 2>&1 | tee -a ${buildLog}
assertCacheMiss ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check11

# Run build 12
echo "Run build 12 - Expect cache hit"
buildLog="/tmp/build12.log"
./mvnw -B clean package -DskipTests -Dnative -Dscan.tag.build12 2>&1 | tee -a ${buildLog}
assertCacheHit ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check12

# Run build 13
echo "Run build 13 - Expect no config dump"
rm -rf .quarkus/*-config-dump
buildLog="/tmp/build13.log"
./mvnw -B clean package -DskipTests -Dscan.tag.build13 2>&1 | tee -a ${buildLog}
assertNoConfigDump ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check13

# Run build 14
echo "Run build 14 - Expect cache hit"
buildLog="/tmp/build14.log"
./mvnw -B clean package -DskipTests -Dscan.tag.build14 2>&1 | tee -a ${buildLog}
assertCacheHit ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check14

# Run build 15
echo "Run build 15 - Expect property changed"
buildLog="/tmp/build15.log"
./mvnw -B clean package -DskipTests -Dnative -Dscan.tag.build15 2>&1 | tee -a ${buildLog}
assertPropertyChanged ${buildLog} "quarkus.package.type"
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check15

# Run build 16
echo "Run build 16 - Expect cache hit"
buildLog="/tmp/build16.log"
./mvnw -B clean package -DskipTests -Dnative -Dscan.tag.build16 2>&1 | tee -a ${buildLog}
assertCacheHit ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check16

# Run build 17
echo "Run build 17 - Expect properties changed"
buildLog="/tmp/build17.log"
quarkusProperty="quarkus.live-reload.retry-interval"
./mvnw -B clean package -DskipTests -Dnative -Dscan.tag.build16 -D${quarkusProperty}=15s 2>&1 | tee -a ${buildLog}
assertPropertyChanged ${buildLog} ${quarkusProperty}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check17

# Run build 18
echo "Run build 18 - Expect cache hit"
buildLog="/tmp/build18.log"
./mvnw -B clean package -DskipTests -Dnative -Dscan.tag.build18 -D${quarkusProperty}=15s 2>&1 | tee -a ${buildLog}
assertCacheHit ${buildLog}
cp target/quarkus-prod-config-check /tmp/quarkus-prod-config-check18

echo "TEST SUCCESSFUL"
