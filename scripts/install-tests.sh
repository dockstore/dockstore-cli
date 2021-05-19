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
    pip3 install --user toil[cwl]==3.15.0
else
    pip3 install --user -r https://raw.githubusercontent.com/dockstore/dockstore/develop/dockstore-webservice/src/main/resources/requirements/1.10.0/requirements3.txt
fi

if [ "${TESTING_PROFILE}" = "singularity-tests" ]; then
    # Install singularity from source
    # need singularity > 3.0.0, which is not available as an ubuntu package
    sudo apt-get update && sudo apt-get install -y \
    build-essential \
    libssl-dev \
    uuid-dev \
    libgpgme11-dev \
    squashfs-tools \
    libseccomp-dev \
    pkg-config

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
