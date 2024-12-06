#!/usr/bin/env bash
# Installs dependencies for integration tests, not used for unit tests
# Includes Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [ "${TESTING_PROFILE}" = "unit-tests" ] || [ "${TESTING_PROFILE}" == "automated-review" ]; then
    exit 0;
fi

if [ "${TESTING_PROFILE}" = "toil-integration-tests" ]; then
    pip3 install --user toil[cwl]==7.0.0
else
    # depending on https://github.com/dockstore/dockstore/pull/5958 we may want to match where we go with the cwltool install, for now apt seems to work well
    sudo apt-get update
    # https://stackoverflow.com/questions/44331836/apt-get-install-tzdata-noninteractive needed by cwltool
    DEBIAN_FRONTEND=noninteractive sudo apt-get -qq --yes --force-yes install tzdata pipx curl
    curl -o requirements.txt "https://dockstore.org/api/metadata/runner_dependencies?client_version=1.16.0&python_version=3"
    pipx install cwltool==3.1.20240708091337
    pipx install schema_salad==8.7.20240718183047
    pipx runpip cwltool install -r requirements.txt # this ensures that your version of cwltool and its dependencies matches what we test with
    pipx ensurepath
fi

if [ "${TESTING_PROFILE}" = "singularity-tests" ]; then
    # Install singularity from source
    # need singularity > 3.0.0, which is not available as an ubuntu package
    # https://askubuntu.com/questions/1367139/apt-get-upgrade-auto-restart-services
    sudo apt-get update && sudo NEEDRESTART_MODE=a apt install \
    build-essential \
    libssl-dev \
    uuid-dev \
    libgpgme11-dev \
    squashfs-tools \
    libseccomp-dev \
    pkg-config

    # do we have conflicting go installations?
    sudo rm -Rf /usr/local/go
    # Install Go (needed to install singularity)
    # Install instructions at https://sylabs.io/guides/3.0/user-guide/installation.html#install-go
    # pick version at https://golang.org/dl/
    export VERSION=1.15.8 OS=linux ARCH=amd64 && \
    wget https://dl.google.com/go/go"${VERSION}"."${OS}"-"${ARCH}".tar.gz && \
    sudo tar -C "${GO_PATH}" -xzvf go"${VERSION}"."${OS}"-"${ARCH}".tar.gz && \
    rm go"${VERSION}"."${OS}"-"${ARCH}".tar.gz

    # If you are installing Singularity v3.0.0 you will also need to install dep for dependency resolution.
    go get -u github.com/golang/dep/cmd/dep

    # Download and install singularity from a release
    # https://sylabs.io/guides/3.0/user-guide/installation.html#download-and-install-singularity-from-a-release
    export VERSION=3.7.3 && # adjust this as necessary \
    mkdir -p "${SINGULARITY_PATH}"/src/github.com/sylabs && \
    cd "${SINGULARITY_PATH}"/src/github.com/sylabs && \
    wget https://github.com/sylabs/singularity/releases/download/v"${VERSION}"/singularity-"${VERSION}".tar.gz && \
    tar -xzf singularity-"${VERSION}".tar.gz && \
    cd ./singularity && \
    ./mconfig

    # Compile singularity
    ./mconfig && \
    make -C ./builddir && \
    sudo make -C ./builddir install
    singularity --version
fi
