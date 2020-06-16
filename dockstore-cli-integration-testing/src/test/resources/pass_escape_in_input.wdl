version 1.0
workflow test_location {
    input {
        String readGroups
    }
    call find_tools {
        input:
            readGroups=readGroups
    }
}
task find_tools {
    input {
        String readGroups
    }
    command <<<
        echo ~{readGroups}
    >>>
    output{
        String result = read_string(stdout())
    }
    runtime {
        docker: "ubuntu:latest"
    }
}
