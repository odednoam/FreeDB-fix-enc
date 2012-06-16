import com.sun.org.apache.xml.internal.utils.StringVector;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/**
 * Created by IntelliJ IDEA.
 * User: odedn
 * Date: 8/25/11
 * Time: 2:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class CddbToCsv
{
    public static void main(String[] args)
    {

        try
        {
            String encoding = "GB18030";
            String inputFile = "e:\\cddb\\output\\" + encoding + ".tar.gz";
            String outputFile = inputFile.replaceAll("\\..*$", ".html");
            TarArchiveInputStream tais = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(inputFile)));
            PrintWriter w = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile),"UTF-8")));
            w.print("<html><body><table>");
            w.printf("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\r\n",
                                     "DTITLE",
                                     "TTITLE0",
                                     "TTITLE1",
                                     "TTITLE2",
                                     "TTITLE3",
                                     "TTITLE4",
                                     "TTITLE5") ;
            TarArchiveEntry nextTarEntry;
            byte buf[] = new byte[150000];
            Properties p = new Properties();
            while ((nextTarEntry = tais.getNextTarEntry()) != null)
            {
                p.clear();
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
                    String s = new String(buf, 0, size, encoding);

                    for (String token : s.split("\n"))
                    {
                        if (token.startsWith("#"))
                        {
                            continue;
                        }
                        String subtokens[] = token.split("=");
                        if (subtokens.length != 2)
                        {
                            continue;
                        }
                        p.put(subtokens[0], subtokens[1]);
                    }
                    w.printf("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\r\n", //""\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\r\n",
                                     p.getProperty("DTITLE", ""),
                                     p.getProperty("TTITLE0", ""),
                                     p.getProperty("TTITLE1", ""),
                                     p.getProperty("TTITLE2", ""),
                                     p.getProperty("TTITLE3", ""),
                                     p.getProperty("TTITLE4", ""),
                                     p.getProperty("TTITLE5", ""));
                }
            }
            w.println("</table></body></html>");
            w.close();
            tais.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
