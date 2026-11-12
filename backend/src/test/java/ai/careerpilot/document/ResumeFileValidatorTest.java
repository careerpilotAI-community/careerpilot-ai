package ai.careerpilot.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.assertj.core.api.Assertions.*;

class ResumeFileValidatorTest {
    private final ResumeFileValidator validator=new ResumeFileValidator();

    @Test void acceptsAParsablePdf() throws Exception {
        byte[] pdf;
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf=output.toByteArray();
        }
        assertThat(validator.validate("resume.pdf",pdf).mediaType()).isEqualTo("application/pdf");
    }

    @Test void rejectsFakePdfAndUnsupportedFormats() {
        assertThatThrownBy(()->validator.validate("resume.pdf","not a pdf".getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->validator.validate("resume.doc","legacy".getBytes())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsOversizedFiles() {
        assertThatThrownBy(()->validator.validate("resume.pdf",new byte[(int)ResumeFileValidator.MAX_BYTES+1]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsStructurallyValidDocxPackage() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"></Types>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>".getBytes());
            zip.closeEntry();
        }
        assertThat(validator.validate("resume.docx",output.toByteArray()).mediaType())
            .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test void rejectsDocxWithMissingRequiredParts() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/other.xml")); zip.write("<root/>".getBytes()); zip.closeEntry();
        }
        assertThatThrownBy(()->validator.validate("resume.docx",output.toByteArray())).isInstanceOf(IllegalArgumentException.class);
    }
}
