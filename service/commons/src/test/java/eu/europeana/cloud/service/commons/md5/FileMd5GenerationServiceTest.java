package eu.europeana.cloud.service.commons.md5;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class FileMd5GenerationServiceTest {

    @Test
    public void shouldGenerateDifferentMd5ForSlightlyDifferentFiles() throws URISyntaxException, IOException {
        UUID record1 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_1.xml").toURI()));
        UUID record2 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_1_1.xml").toURI()));
        assertNotSame(record1, record2);
    }

    @Test
    public void shouldGenerateDifferentMd5ForSlightlyDifferentFilesProvidedAsByteArrays() throws IOException {
        UUID record1 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_1.xml").getFile())));
        UUID record2 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_1_1.xml").getFile())));
        assertNotSame(record1, record2);
    }

    @Test
    public void shouldGenerateTheSameMd5ForSameFile() throws URISyntaxException, IOException {
        UUID record1 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_1.xml").toURI()));
        UUID record2 = FileMd5GenerationService.generate(Paths.get(getClass().getClassLoader().getResource("Lithuania_1.xml").toURI()));
        assertEquals(record1, record2);
    }

    @Test
    public void shouldGenerateTheSameMd5ForSameFileProvidedAsByteArray() throws IOException {
        UUID record1 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_1.xml").getFile())));
        UUID record2 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_1.xml").getFile())));
        assertEquals(record1, record2);
    }

    @Test
    public void shouldGenerateDifferentMd5ForDifferentFiles() throws URISyntaxException, IOException {
        UUID record1_1 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_1_1.xml").toURI()));
        UUID record1 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_1.xml").toURI()));
        UUID record2 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_2.xml").toURI()));
        UUID record3 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_3.xml").toURI()));
        UUID record4 = FileMd5GenerationService.generate(Paths.get(ClassLoader.getSystemResource("Lithuania_4.xml").toURI()));
        assertNotSame(record1, record2);
        assertNotSame(record1, record1_1);
        assertNotSame(record1, record3);
        assertNotSame(record1, record4);
        assertNotSame(record1_1, record2);
        assertNotSame(record1_1, record3);
        assertNotSame(record1_1, record4);
        assertNotSame(record2, record3);
        assertNotSame(record2, record4);
        assertNotSame(record3, record4);
    }

    @Test
    public void shouldGenerateDifferentMd5ForDifferentFilesProvidedAsByteArrays() throws IOException {
        UUID record1 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_1.xml").getFile())));
        UUID record1_1 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_1_1.xml").getFile())));
        UUID record2 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_2.xml").getFile())));
        UUID record3 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_3.xml").getFile())));
        UUID record4 = FileMd5GenerationService.generate(FileUtils.readFileToByteArray(new File(ClassLoader.getSystemResource("Lithuania_4.xml").getFile())));

        assertNotSame(record1, record2);
        assertNotSame(record1, record1_1);
        assertNotSame(record1, record3);
        assertNotSame(record1, record4);
        assertNotSame(record1_1, record2);
        assertNotSame(record1_1, record3);
        assertNotSame(record1_1, record4);
        assertNotSame(record2, record3);
        assertNotSame(record2, record4);
        assertNotSame(record3, record4);
    }
}