import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.mozilla.universalchardet.Constants;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.simple.JSONObject;
public class PrepareJsonForAppengine
{
    public static void main(String[] args)
    {
        try
        {
            TarArchiveInputStream tais = new TarArchiveInputStream(new BZip2CompressorInputStream(new FileInputStream("c:\\cddb\\output\\cddb-non-latin.tar.bz2")));

            TarArchiveEntry nextTarEntry;
            byte buf[] = new byte[150000];
            byte trimmedBuf[] = new byte[150000];
            File output = new File("c:\\cddb\\output\\suspected.json");
            BufferedWriter jsonWriter = new BufferedWriter(new FileWriter(output));
            output.getParentFile().mkdir();
            CharsetDetector icuDetector = new CharsetDetector();

            Map<String,AtomicLong> statistics = new HashMap<String, AtomicLong>();
            statistics.put("null", new AtomicLong(0));
            long totalFiles = 0;
            Set<String> unsupportedEncodings = new HashSet<String>();
            unsupportedEncodings.add("UTF-8");
            unsupportedEncodings.add("ISO-8859-1");
            unsupportedEncodings.add("ISO-8859-2");
            unsupportedEncodings.add("WINDOWS-1250");
            unsupportedEncodings.add("WINDOWS-1252");


            Map<String,String> normalizedEncodingNames = new HashMap<String, String>();
            for (Field f : Constants.class.getFields())
            {
                normalizedEncodingNames.put(f.get(null).toString(), f.get(null).toString());
            }
            while ((nextTarEntry = tais.getNextTarEntry()) != null)
            {
                if ((++totalFiles % 10000)==0)
                {
                    System.out.printf("\rtotalFiles: %,d", totalFiles);
                    System.out.flush();
                }

                if (! nextTarEntry.isFile())
                {
                    continue;
                }
                if (nextTarEntry.getSize() > buf.length)
                {
                    System.err.println("Not enough memory to read file: " + nextTarEntry.getName() + " (size " + nextTarEntry.getSize() + ")");
                }
                else
                {
                    int size = (int) nextTarEntry.getSize();
                    tais.read(buf, 0, size);
                    byte[] trimmedShortBuf = parseDbEntry(buf, trimmedBuf, size);
                    CharsetMatch icuMatch = icuDetector.setText(trimmedShortBuf).detect();

                    String icuEncoding = icuMatch==null?"null":icuMatch.getName().toUpperCase();
                    String icuLanguage = (icuMatch==null || icuMatch.getLanguage() == null)?null:icuMatch.getLanguage().toLowerCase();
                    if (icuEncoding == null)
                    {
                        icuEncoding = "null";
                    }
                    
                    String jucEncoding = MozDetectFileCharset.instance.detectCharset(trimmedBuf, trimmedShortBuf.length);
                    if (jucEncoding == null)
                    {
                        jucEncoding = "null";
                    }


                    String encodedAsIcu = tryEncodeInSuspectedCharset(trimmedShortBuf, icuEncoding);
                    String encodedAsJuc = tryEncodeInSuspectedCharset(trimmedShortBuf, jucEncoding);
                    
                    if (encodedAsJuc == null || encodedAsJuc.equals(encodedAsIcu))
                    {
                        if (unsupportedEncodings.contains(icuEncoding) || unsupportedEncodings.contains(jucEncoding))
                        {
                            // The encoded string is equal to its latin encoding, no value in detecting its encoding
                            encodedAsIcu = null;
                            icuEncoding = "null";
   
                        }
                        encodedAsJuc = null;
                        jucEncoding = "null";
                    }
                    
                    if (unsupportedEncodings.contains(icuEncoding))
                    {
                        icuEncoding = "null";
                        encodedAsIcu = null;
                    }

                    if (unsupportedEncodings.contains(jucEncoding))
                    {
                        jucEncoding = "null";
                        encodedAsJuc = null;
                    }

                    registerStatistic(statistics, icuEncoding);
                    registerStatistic(statistics, jucEncoding);

                    String filename = nextTarEntry.getName();
                    writeSuspect(jsonWriter, icuEncoding, encodedAsIcu, icuLanguage, filename);
                    writeSuspect(jsonWriter, jucEncoding, encodedAsJuc, icuLanguage, filename);
                }
            }
            System.out.println();
            jsonWriter.close();

            for (Map.Entry<String,AtomicLong> e : statistics.entrySet())
            {
                if (!e.getKey().contains(" or ") && e.getValue().get()  > 0)
                {
                    System.out.printf("%40s: %,12d (%3.2f%%)\n", e.getKey(), e.getValue().get(), 100.0 * e.getValue().get() / totalFiles );
                }
            }

            for (Map.Entry<String,AtomicLong> e : statistics.entrySet())
            {
                if (e.getKey().contains(" or ") && e.getValue().get()  > 0)
                {
                    System.out.printf("%40s: %,12d (%3.2f%%)\n", e.getKey(), e.getValue().get(), 100.0 * e.getValue().get() / totalFiles );
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (IllegalAccessException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private static byte[] parseDbEntry(byte[] buf, byte[] trimmedBuf, int size) {
        int trimmedSize = 0;
        int previousDelimiter = -1;
        int previousEqualsSign = -1;
        for (int i=0; i<size; ++i)
        {
            if (buf[i] == '=')
            {
                previousEqualsSign = i;
            }
            else if (buf[i] == '\n')
            {
                if (i-previousDelimiter > 1)
                {
                    if (buf[previousDelimiter + 1] != '#' && previousEqualsSign > previousDelimiter)
                    {
                        if (previousEqualsSign-previousDelimiter == 7 && new String(buf, previousDelimiter, 7).equals("DISCID"))
                        {
                            // skip
                        }
                        else
                        {
                            int length = i - previousEqualsSign;
                            if (length > 1)
                            {
                                System.arraycopy(buf, previousEqualsSign + 1, trimmedBuf, trimmedSize, length);
                                trimmedSize += length;
                            }
                        }
                    }
                }
                previousDelimiter = i;

            }
        }

        byte trimmedShortBuf[] = new byte[trimmedSize];
        System.arraycopy(trimmedBuf, 0, trimmedShortBuf, 0, trimmedShortBuf.length);
        return trimmedShortBuf;
    }

    private static String reverse(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (int i=s.length(); i>0; --i)
        {
            sb.append(s.charAt(i-1));
        }
        return sb.toString();
    }

    private static String tryEncodeInSuspectedCharset(byte[] trimmedShortBuf, String charset) {
        String encoded = null;
        try {
            encoded = (charset.equals("null")?null:new String(trimmedShortBuf, charset));
        } catch (UnsupportedEncodingException e) {
            if (charset.endsWith("_RTL"))
            {
                return tryEncodeInSuspectedCharset(trimmedShortBuf, charset.substring(0, charset.length() - 4));
            }
            else if (charset.endsWith("_LTR"))
            {
                return reverse(tryEncodeInSuspectedCharset(trimmedShortBuf, charset.substring(0, charset.length() - 4)));
            }
            e.printStackTrace();
            encoded = null;
        }
        return encoded;
    }

    private static void writeSuspect(BufferedWriter jsonWriter, String encoding, String encodedString, String suspectedLanguage, String filename) throws IOException {
        if (encodedString != null)
        {
            JSONObject obj = new JSONObject();
            obj.put("file", filename);
            obj.put("testString", encodedString);
            obj.put("charset", encoding);
            obj.put("suspectedLangauge", suspectedLanguage);
            obj.writeJSONString(jsonWriter);
            jsonWriter.write('\n');
        }
    }

    private static void registerStatistic(Map<String, AtomicLong> statistics, String encoding) {
        if (! encoding.equals("null"))
        {
            if (! statistics.containsKey(encoding)  )
            {
                statistics.put(encoding,  new AtomicLong(0));
            }
            statistics.get(encoding).incrementAndGet();
        }
    }
}
