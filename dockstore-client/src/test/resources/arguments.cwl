cwlVersion: v1.0
class: CommandLineTool
baseCommand: javac
hints:
  - class: DockerRequirement
    dockerPull: adoptopenjdk/openjdk11:alpine
arguments:
  - prefix: "-d"
    valueFrom: $(runtime.outdir)
inputs:
  - id: src
    type: File
    inputBinding:
      position: 1
outputs:
  - id: classfile
    type: File
    outputBinding:
      glob: "*.class"
