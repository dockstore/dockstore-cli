#!/usr/bin/env bash
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
mkdir -p $GOPATH/src/github.com/sylabs && \
cd $GOPATH/src/github.com/sylabs && \
wget https://github.com/sylabs/singularity/releases/download/v${VERSION}/singularity-${VERSION}.tar.gz && \
tar -xzf singularity-${VERSION}.tar.gz && \
cd ./singularity && \
./mconfig

# Compile singularity
./mconfig && \
make -C ./builddir && \
sudo make -C ./builddir install

singularity --version
which singularity
