package ai.onnxruntime;
import java.util.*;
public class OrtSession implements AutoCloseable {
    public static class SessionOptions implements AutoCloseable {
        public enum OptLevel { NO_OPT, BASIC_OPT, EXTENDED_OPT, ALL_OPT }
        public void setIntraOpNumThreads(int n) throws OrtException {}
        public void setOptimizationLevel(OptLevel level) throws OrtException {}
        public void close() {}
    }
    public static class Result implements AutoCloseable, Iterable<Map.Entry<String, OnnxValue>> {
        public OnnxValue get(int index) { return null; }
        public Iterator<Map.Entry<String, OnnxValue>> iterator() { return null; }
        public void close() {}
    }
    public Set<String> getInputNames() { return null; }
    public Set<String> getOutputNames() { return null; }
    public Map<String, NodeInfo> getInputInfo() throws OrtException { return null; }
    public Map<String, NodeInfo> getOutputInfo() throws OrtException { return null; }
    public Result run(Map<String, ? extends OnnxTensor> inputs) throws OrtException { return null; }
    public void close() {}
}
