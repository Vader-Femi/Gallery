package ai.onnxruntime;
public class TensorInfo implements ValueInfo {
    public final OnnxJavaType type = OnnxJavaType.FLOAT;
    public long[] getShape() { return new long[0]; }
}
