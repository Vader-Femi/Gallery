package ai.onnxruntime;
import java.nio.*;
public class OnnxTensor implements OnnxValue {
    public Object getValue() { return null; }
    public void close() {}
    public static OnnxTensor createTensor(OrtEnvironment env, FloatBuffer data, long[] shape) throws OrtException { return null; }
    public static OnnxTensor createTensor(OrtEnvironment env, LongBuffer data, long[] shape) throws OrtException { return null; }
    public static OnnxTensor createTensor(OrtEnvironment env, IntBuffer data, long[] shape) throws OrtException { return null; }
}
