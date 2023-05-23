version 1.0
# This workflow test was taken directly from here https://github.com/openwdl/learn-wdl/tree/master/1_script_examples/1_hello_worlds/7_subworkflow

workflow HelloTask {
    # Lines 5-7 are optional. Added for readability
    input {
        String name
    }
    call WriteGreeting
}

task WriteGreeting {
    input {
        String name
    }
    command {
        echo 'hello ${name}!'
    }
    output {
        File response = stdout()
    }
}


