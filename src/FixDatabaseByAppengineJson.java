import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.mozilla.universalchardet.Constants;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FixDatabaseByAppengineJson
{
    public static void main(String[] args)
    {
        
        try
        {
            if (args.length != 1)
            {
                System.err.println("Argument required: extracted JSON file from web application");
                return;
            }
            File inputFile = new File(args[0]);
            BufferedReader r = new BufferedReader(new FileReader(inputFile));
            String line;
            Map<String, String> targetEncoding = new HashMap<String, String>();
            JSONParser parser = new JSONParser();
            while ((line = r.readLine())!=null)
            {
                JSONObject obj = (JSONObject) parser.parse(line);
                String key[] = obj.get("key").toString().split("@");
                int approved = Integer.parseInt(obj.get("approved").toString());
                int rejected = Integer.parseInt(obj.get("rejected").toString());
                if (rejected == 0 && approved > 0)
                {
                    String conflict = targetEncoding.put(key[0], key[1]);
                    if (conflict != null)
                    {
                        System.err.println("File " + key[0] + " was approved as both " + conflict + " and " + key[1]);
                        System.err.println("File " + key[0] + " was approved as both " + conflict + " and " + key[1]);
                        targetEncoding.remove(key[0]);
                    }
                }
            }

            TarArchiveInputStream tais = new TarArchiveInputStream(new BZip2CompressorInputStream(new FileInputStream("c:\\cddb\\output\\cddb-non-latin.tar.bz2")));
            TarArchiveOutputStream outputTar = new TarArchiveOutputStream(new BZip2CompressorOutputStream(new FileOutputStream("c:\\cddb\\output\\" + inputFile.getName().replaceFirst("\\.[^\\.]+$","") + ".tar.bz2")));

            TarArchiveEntry nextTarEntry;
            byte buf[] = new byte[150000];
            byte trimmedBuf[] = new byte[150000];
            Set<String> reEncoded = new HashSet<String>();

            long totalFiles = 0;
            Pattern revisionStringPattern = Pattern.compile("^# Revision: (\\d+)$", Pattern.MULTILINE);
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
                    
                    if (targetEncoding.containsKey(nextTarEntry.getName()))
                    {
                        String charset = targetEncoding.remove(nextTarEntry.getName());
                        tais.read(buf, 0, size);
                        String newEntry = new String(buf, 0, size, charset);
                        Matcher matcher = revisionStringPattern.matcher(newEntry);
                        if (! matcher.find())
                        {
                            System.err.println("Revision string not found! in " + nextTarEntry.getName());
                            System.err.println("Entry contents:");
                            System.err.println(newEntry);
                            System.exit(1);
                        }
                        
                        String revisionUpdated =  matcher.replaceFirst("# Revision: " + Integer.toString(Integer.parseInt(matcher.group(1))+1));
                        byte[] asUtf8 = revisionUpdated.getBytes("UTF-8");
                        reEncoded.add(nextTarEntry.getName());
                        nextTarEntry.setSize(asUtf8.length);
                        outputTar.putArchiveEntry(nextTarEntry);
                        outputTar.write(asUtf8, 0, asUtf8.length);
                        outputTar.closeArchiveEntry();
                    }
                    else
                    {
                        tais.skip(size);
                    }
                }
            }
            outputTar.close();
            tais.close();
            System.out.println();
        }
        catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (ParseException e)
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
