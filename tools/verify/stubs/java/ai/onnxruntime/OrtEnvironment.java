package ai.onnxruntime;
public class OrtEnvironment implements AutoCloseable {
    public static OrtEnvironment getEnvironment() { return new OrtEnvironment(); }
    public OrtSession createSession(String path, OrtSession.SessionOptions options) throws OrtException { return null; }
    public void close() {}
}
