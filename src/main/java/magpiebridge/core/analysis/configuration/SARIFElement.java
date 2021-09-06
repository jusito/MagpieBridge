package magpiebridge.core.analysis.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.util.collections.Pair;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.Kind;
import magpiebridge.core.SARIFResult;
import magpiebridge.util.JsonFormatHandler;

import org.eclipse.lsp4j.DiagnosticSeverity;

/** Converts SARIF JSON to analysis results */
public class SARIFElement {
  private List<AnalysisResult> analysisResults;

  public SARIFElement(JsonObject sarif) throws MalformedURLException {
    this.analysisResults = new ArrayList<AnalysisResult>();
    if (JsonFormatHandler.notNullAndHas(sarif, "runs")) {
      JsonArray runs = sarif.get("runs").getAsJsonArray();
      JsonArray results = new JsonArray();
      JsonObject run = new JsonObject();
      JsonObject result = new JsonObject();
      for (int i = 0; i < runs.size(); i++) {
        run = runs.get(i).getAsJsonObject();
        if (JsonFormatHandler.notNullAndHas(run, "results")) {
          results = run.get("results").getAsJsonArray();
          for (int j = 0; j < results.size(); j++) {
            result = results.get(j).getAsJsonObject();
            insertAnalysisResult(result);
          }
        }
      }
    }
  }

  public List<AnalysisResult> getAnalysisResults() {
    return analysisResults;
  }

  private void insertAnalysisResult(JsonObject result) throws MalformedURLException {
    JsonObject message = result.get("message").getAsJsonObject();
    JsonArray locations = result.get("locations").getAsJsonArray();
    String messageText = JsonFormatHandler.notNullAndHas(message, "text") ? message.get("text").getAsString() : null;
    Position errorPosition = locationToPosition(locations.get(0).getAsJsonObject());

    if (errorPosition == null || messageText == null) {
      return;
    }

    List<Pair<Position, String>> relatedInfo = new ArrayList<>();
    String code = locationToCode(locations.get(0).getAsJsonObject());

    if (JsonFormatHandler.notNullAndHas(result, "codeFlows")) {
      JsonArray codeFlows = result.get("codeFlows").getAsJsonArray();
      JsonObject codeFlow = new JsonObject();
      JsonArray threadFlows = new JsonArray();
      JsonObject threadFlow = new JsonObject();
      JsonArray threadFlowLocations = new JsonArray();
      for (int i = 0; i < codeFlows.size(); i++) {
        codeFlow = codeFlows.get(i).getAsJsonObject();
        threadFlows =
        		JsonFormatHandler.notNullAndHas(codeFlow, "threadFlows")
                ? codeFlow.get("threadFlows").getAsJsonArray()
                : null;
        if (threadFlows != null) {
          for (int j = 0; j < threadFlows.size(); j++) {
            threadFlow = threadFlows.get(j).getAsJsonObject();
            threadFlowLocations =
            		JsonFormatHandler.notNullAndHas(threadFlow, "locations")
                    ? threadFlow.get("locations").getAsJsonArray()
                    : null;
            if (threadFlowLocations != null) {
              relatedInfo = locationsToFlow(threadFlowLocations);
              if (code == null && relatedInfo != null && relatedInfo.size() != 0) {
                int lastLocation = relatedInfo.size() - 1;
                code = relatedInfo.get(lastLocation).snd;
              }

              analysisResults.add(
                  new SARIFResult(
                      Kind.Diagnostic,
                      errorPosition,
                      messageText,
                      relatedInfo,
                      DiagnosticSeverity.Error,
                      null,
                      code,
                      null));
            }
          }
        }
      }
    }

    // if the analysis result is empty add error with error position and message.
    if (analysisResults.isEmpty()) {
      analysisResults.add(
          new SARIFResult(
              Kind.Diagnostic,
              errorPosition,
              messageText,
              relatedInfo,
              DiagnosticSeverity.Error,
              null,
              code,
              null));
    }
  }

  private List<Pair<Position, String>> locationsToFlow(JsonArray locations)
      throws MalformedURLException {
    List<Pair<Position, String>> flows = new ArrayList<>();
    JsonObject locationWrap = new JsonObject();
    JsonObject location = new JsonObject();
    String code;

    for (int i = 0; i < locations.size(); i++) {
      locationWrap = locations.get(i).getAsJsonObject();
      location = locationWrap.get("location").getAsJsonObject();
      code = locationToCode(location);
      Position position = locationToPosition(location);
      Pair<Position, String> pair = Pair.make(position, code);
      flows.add(pair);
    }

    return flows.isEmpty() ? null : flows;
  }

  private Position locationToPosition(JsonObject location) throws MalformedURLException {
    if (!JsonFormatHandler.notNullAndHas(location, "physicalLocation")) {
      return null;
    }

    JsonObject phsicalLocation = location.getAsJsonObject("physicalLocation");
    JsonObject artifactLocation =
    		JsonFormatHandler.notNullAndHas(phsicalLocation, "artifactLocation")
            ? phsicalLocation.getAsJsonObject("artifactLocation")
            : null;
    JsonObject region =
    		JsonFormatHandler.notNullAndHas(phsicalLocation, "region") ? phsicalLocation.getAsJsonObject("region") : null;

    int firstLine = JsonFormatHandler.notNullAndHas(region, "startLine") ? region.get("startLine").getAsInt() : -1;
    int lastLine = JsonFormatHandler.notNullAndHas(region, "endLine") ? region.get("endLine").getAsInt() : -1;
    int firstCol = JsonFormatHandler.notNullAndHas(region, "startColumn") ? region.get("startColumn").getAsInt() : -1;
    int lastCol = JsonFormatHandler.notNullAndHas(region, "endColumn") ? region.get("endColumn").getAsInt() : -1;
    URL url =
    		JsonFormatHandler.notNullAndHas(artifactLocation, "uri")
            ? new URL(artifactLocation.get("uri").getAsString())
            : null;
    SARIFCodePosition position = new SARIFCodePosition(firstLine, firstCol, lastLine, lastCol, url);
    return position;
  }

  private String locationToCode(JsonObject location) {
    JsonObject message =
    		JsonFormatHandler.notNullAndHas(location, "message") ? location.getAsJsonObject("message") : null;
    String text = JsonFormatHandler.notNullAndHas(message, "text") ? message.get("text").toString() : null;
    return text;
  }

}
