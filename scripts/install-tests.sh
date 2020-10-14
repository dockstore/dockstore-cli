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
elif [ "${TESTING_PROFILE}" = "regression-integration-tests" ]; then
    pip3 install --user -r https://raw.githubusercontent.com/dockstore/dockstore/develop/dockstore-webservice/src/main/resources/requirements/1.6.0/requirements3.txt
else
    pip3 install --user -r https://raw.githubusercontent.com/dockstore/dockstore/feature/3196/updateCWLtool/dockstore-webservice/src/main/resources/requirements/1.10.0/requirements3.txt
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
    export VERSION=1.11 OS=linux ARCH=amd64 && \
    wget https://dl.google.com/go/go$VERSION.$OS-$ARCH.tar.gz && \
    sudo tar -C /usr/local -xzvf go$VERSION.$OS-$ARCH.tar.gz && \
    rm go$VERSION.$OS-$ARCH.tar.gz

    # Download and install singularity from a release
    export VERSION=3.0.3 && # adjust this as necessary \
    mkdir -p "$GOPATH"/src/github.com/sylabs && \
    cd "$GOPATH"/src/github.com/sylabs && \
    wget https://github.com/sylabs/singularity/releases/download/v${VERSION}/singularity-${VERSION}.tar.gz && \
    tar -xzf singularity-${VERSION}.tar.gz && \
    cd ./singularity && \
    ./mconfig

    # Compile singularity
    ./mconfig && \
    make -C ./builddir && \
    sudo make -C ./builddir install
    singularity --version
fi

# hook up integration tests with elastic search
docker pull elasticsearch:5.6.3
docker run -p 9200:9200 -d elasticsearch:5.6.3
