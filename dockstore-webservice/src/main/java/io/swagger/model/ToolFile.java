/*
 * GA4GH Tool Discovery API
 * Proposed API for GA4GH tool repositories. A tool consists of a (currently Docker) image paired with a document that describes how to use that image (currently CWL or WDL) and a Dockerfile that describes how to re-produce the image in the future. We use the following terminology, an \"image\" describes a (Docker) container as stored on a filesystem, a \"tool\" describes one of the triples as described above, and a \"container\" should only be used to describe a running image
 *
 * OpenAPI spec version: 2.0.0
 *
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package io.swagger.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.*;

/**
 * ToolFile
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-01-09T22:21:23.662Z")
public class ToolFile   {
  @JsonProperty("path")
  private String path = null;

  /**
   * Gets or Sets fileType
   */
  public enum FileTypeEnum {
    TEST_FILE("TEST_FILE"),

    PRIMARY_DESCRIPTOR("PRIMARY_DESCRIPTOR"),

    SECONDARY_DESCRIPTOR("SECONDARY_DESCRIPTOR"),

    DOCKERFILE("DOCKERFILE"),

    OTHER("OTHER");

    private String value;

    FileTypeEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static FileTypeEnum fromValue(String text) {
      for (FileTypeEnum b : FileTypeEnum.values()) {
        if (String.valueOf(b.value).equals(text)) {
          return b;
        }
      }
      return null;
    }
  }

  @JsonProperty("file_type")
  private FileTypeEnum fileType = null;

  public ToolFile path(String path) {
    this.path = path;
    return this;
  }

  /**
   * relative path of the file that can be used with the GA4GH .../{type}/descriptor/{relative_path} endpoint if it&#39;s a descriptor
   * @return path
   **/
  @JsonProperty("path")
  @ApiModelProperty(value = "relative path of the file that can be used with the GA4GH .../{type}/descriptor/{relative_path} endpoint if it's a descriptor")
  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public ToolFile fileType(FileTypeEnum fileType) {
    this.fileType = fileType;
    return this;
  }

  /**
   * Get fileType
   * @return fileType
   **/
  @JsonProperty("file_type")
  @ApiModelProperty(value = "")
  public FileTypeEnum getFileType() {
    return fileType;
  }

  public void setFileType(FileTypeEnum fileType) {
    this.fileType = fileType;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolFile toolFile = (ToolFile) o;
    return Objects.equals(this.path, toolFile.path) &&
            Objects.equals(this.fileType, toolFile.fileType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, fileType);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolFile {\n");

    sb.append("    path: ").append(toIndentedString(path)).append("\n");
    sb.append("    fileType: ").append(toIndentedString(fileType)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
