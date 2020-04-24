## How WES OpenApi 3.0 yaml was created from Swagger 2.0 yaml

SwaggerHub was used to convert the WES 1.0.0 Swagger 2.0 yaml to OpenAPI 3.0 yaml

#### Steps to convert:
1. Launch SwaggerHub https://app.swaggerhub.com/home
1. Create an account and login to SwaggerHub
1. Click on the 'plus' (**+**) icon on the left side of the page
1. Select 'Import and Document API'
1. In the Import API dialog paste the WES Swagger 2.0 URL: 
https://raw.githubusercontent.com/ga4gh/workflow-execution-service-schemas/1.0.0/openapi/workflow_execution_service.swagger.yaml into the
 'Path or URL' edit box
1. Click 'Import'
..You should now be able to see the The WES Swagger 2.0 yaml in the editor
1. Click on the green box with the down arrow at the top right of the editor
1. Select 'Convert to OpenAPI 3.0'
1. In the 'Convert to OpenAPI 3.0?' dialog click 'Convert & Update'
1. Click the 'Export' button on the uppper right hand area of the page
1. Select '< Download API' and then 'YAML Unresolved' ..The yaml file will be downloaded to your local machine
