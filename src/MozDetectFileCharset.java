//import com.ibm.icu.text.CharsetDetector;
//import com.ibm.icu.text.CharsetMatch;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.UniversalDetector;

public class MozDetectFileCharset implements CharsetListener
{
    String detectedCharset = null;
    public static final MozDetectFileCharset instance = new MozDetectFileCharset();
    private final UniversalDetector detector;

    private MozDetectFileCharset()
    {
        detector = new UniversalDetector(this);
    }

    public String detectCharset(byte[] fileContents, int length)
    {
        detectedCharset = null;
        detector.reset();
        detector.handleData(fileContents, 0, length);
        detector.dataEnd();
        return detectedCharset;
    }

    public void report(String name)
    {
        detectedCharset = name;
    }

}
