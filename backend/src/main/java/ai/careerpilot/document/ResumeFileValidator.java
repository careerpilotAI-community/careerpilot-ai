package ai.careerpilot.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;

@Component
public class ResumeFileValidator {
    public static final long MAX_BYTES=10L*1024*1024;
    public ValidatedFile validate(String filename, byte[] bytes) {
        if (bytes==null || bytes.length==0 || bytes.length>MAX_BYTES) throw new IllegalArgumentException("File must be between 1 byte and 10 MiB.");
        String lower=filename==null?"":filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) { validatePdf(bytes); return new ValidatedFile("application/pdf"); }
        if (lower.endsWith(".docx")) { validateDocx(bytes); return new ValidatedFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document"); }
        throw new IllegalArgumentException("Only PDF and DOCX files are accepted.");
    }
    private void validatePdf(byte[] bytes) {
        try (PDDocument document=Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || document.getNumberOfPages()<1)
                throw new IllegalArgumentException("Encrypted or empty PDF documents are not accepted.");
        } catch (IOException ex) {
            throw new IllegalArgumentException("The PDF is malformed or incomplete.",ex);
        }
    }
    private void validateDocx(byte[] bytes) {
        if (bytes.length<4 || bytes[0]!='P' || bytes[1]!='K') throw new IllegalArgumentException("The DOCX package is invalid.");
        Set<String> required=new HashSet<>(); long expanded=0; int entries=0;
        try (ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry=zip.getNextEntry())!=null) {
                if (++entries>2000) throw new IllegalArgumentException("DOCX contains too many package entries.");
                String name=entry.getName();
                if (name.startsWith("/") || name.contains("..") || name.contains("\\")) throw new IllegalArgumentException("DOCX contains an unsafe package path.");
                boolean requiredPart=name.equals("[Content_Types].xml") || name.equals("word/document.xml");
                ByteArrayOutputStream part=requiredPart?new ByteArrayOutputStream():null;
                byte[] buffer=new byte[4096]; int n;
                while ((n=zip.read(buffer))!=-1) {
                    expanded+=n;
                    if(expanded>50L*1024*1024) throw new IllegalArgumentException("DOCX expands beyond the safe processing limit.");
                    if(part!=null) part.write(buffer,0,n);
                }
                if(part!=null){validateXml(part.toByteArray());required.add(name);}
                zip.closeEntry();
            }
        } catch (IOException ex) { throw new IllegalArgumentException("The DOCX package is corrupt.",ex); }
        if (!required.contains("[Content_Types].xml") || !required.contains("word/document.xml"))
            throw new IllegalArgumentException("The DOCX package is missing required document parts.");
    }
    private void validateXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch(Exception ex) { throw new IllegalArgumentException("The DOCX contains invalid or unsafe XML.",ex); }
    }
    public record ValidatedFile(String mediaType) {}
}
