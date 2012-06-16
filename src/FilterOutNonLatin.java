import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.mozilla.universalchardet.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FilterOutNonLatin
{
    public static void main(String[] args)
    {
        try
        {
            TarArchiveInputStream tais = new TarArchiveInputStream(new BZip2CompressorInputStream(new FileInputStream("c:\\cddb\\freedb-complete-20120601.tar.bz2")));

            TarArchiveEntry nextTarEntry;
            byte buf[] = new byte[150000];
            File output = new File("c:\\cddb\\output\\cddb-non-latin.tar.bz2");
            output.getParentFile().mkdir();
            TarArchiveOutputStream outputTar = new TarArchiveOutputStream(new BZip2CompressorOutputStream(new FileOutputStream(output)));
            long numLatin = 0;
            long numNonLatin = 0;
            long totalFiles = 0;

            while ((nextTarEntry = tais.getNextTarEntry()) != null)
            {
                if ((totalFiles % 100000)==0)
                {
                    System.out.printf("\rProcessed %d files", totalFiles);
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

                    boolean latin = true;
                    for (int i=0; i<size; ++i)
                    {
                        if (((int)buf[i]&0xff) >= 160)
                        {
                            latin = false;
                            break;
                        }
                    }

                    ++totalFiles;
                    if (latin)
                    {
                        ++numLatin;
                    }
                    else
                    {
                        ++numNonLatin;
                        outputTar.putArchiveEntry(nextTarEntry);
                        outputTar.write(buf, 0, size);
                        outputTar.closeArchiveEntry();
                    }
                }
            }

            outputTar.close();
            System.out.println();
            System.out.printf("Latin:     %,12d (%3.2f%%)\n", numLatin, 100.0*numLatin/totalFiles);
            System.out.printf("Non-Latin: %,12d (%3.2f%%)\n", numNonLatin, 100.0*numNonLatin/totalFiles);
            System.out.printf("Total:     %,12d\n", totalFiles);
        }
        catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
