cwlVersion: v1.2
class: Workflow
requirements:
  InlineJavascriptRequirement: {}

inputs:
  - id: inp
    type: File
  - id: ex
    type: string

outputs:
  - id: classout
    type: File
    outputSource: "#compile/classfile"

steps:
  - id: untar
    run: tar-param.cwl
    in:
      - id: tarfile
        source: "#inp"
      - id: extractfile
        source: "#ex"
    out:
      - id: example_out

  # "when" is new in CWL 1.2
  - id: compile
    when: $(true)
    run: arguments.cwl
    in:
      - id: src
        source: "#untar/example_out"
    out:
      - id: classfile
    
