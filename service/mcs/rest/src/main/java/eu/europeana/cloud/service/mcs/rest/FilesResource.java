package eu.europeana.cloud.service.mcs.rest;

import eu.europeana.cloud.common.model.File;
import eu.europeana.cloud.service.mcs.RecordService;
import eu.europeana.cloud.service.mcs.exception.CannotModifyPersistentRepresentationException;
import eu.europeana.cloud.service.mcs.exception.FileAlreadyExistsException;
import eu.europeana.cloud.service.mcs.exception.FileNotExistsException;
import eu.europeana.cloud.service.mcs.exception.RepresentationNotExistsException;
import eu.europeana.cloud.service.mcs.utils.EnrichUriUtil;
import eu.europeana.cloud.service.mcs.utils.storageSelector.PreBufferedInputStream;
import eu.europeana.cloud.service.mcs.utils.storageSelector.StorageSelector;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static eu.europeana.cloud.common.web.ParamConstants.*;
import static eu.europeana.cloud.service.mcs.utils.storageSelector.PreBufferedInputStream.wrap;

/**
 * FilesResource
 */
@RestController
@RequestMapping("/records/{"+CLOUD_ID+"}/representations/{"+REPRESENTATION_NAME+"}/versions/{"+VERSION+"}/files")
@Scope("request")
public class FilesResource {
	private static final Logger LOGGER = LoggerFactory.getLogger(FilesResource.class.getName());

	@Autowired
	private RecordService recordService;
	@Autowired
	private MutableAclService mutableAclService;
	@Autowired
	private Integer objectStoreSizeThreshold;

	/**
	 * Adds a new file to representation version. URI to created resource will
	 * be returned in response as content location. Consumes multipart content -
	 * form data:
	 * <ul>
	 * <li>{@value eu.europeana.cloud.common.web.ParamConstants#F_FILE_MIME} -
	 * file mime type</li>
	 * <li>{@value eu.europeana.cloud.common.web.ParamConstants#F_FILE_NAME} -
	 * file name</li>
	 * <li>{@value eu.europeana.cloud.common.web.ParamConstants#F_FILE_DATA} -
	 * binary stream of file content (required)</li>
	 * </ul>
	 *
	 * <strong>Write permissions required.</strong>
	 * @summary Add a new file to a representation version
	 * @param cloudId cloud id of the record (required).
	 * @param representationName schema of representation (required).
	 * @param version a specific version of the representation(required).
	 * @param mimeType
	 *            mime type of file
	 * @param data
	 *            binary stream of file content (required)
	 * @param fileName
	 *            name of creating file. If fileName does not provided by POST
	 *            request fileName will assigned automatically by service.
	 * @return empty response with tag (content md5) and URI to the created resource
	 *         in content location.
	 * @statuscode 201 object has been created.
	 * @throws RepresentationNotExistsException
	 *             representation does not exist in specified version
	 * @throws CannotModifyPersistentRepresentationException
	 *             specified representation version is persistent and modifying
	 *             its files is not allowed.
	 * @throws FileAlreadyExistsException
	 *             specified file already exist.
	 */
	@PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasPermission(#cloudId.concat('/').concat(#representationName).concat('/').concat(#version),"
    		+ " 'eu.europeana.cloud.common.model.Representation', write)")
	public ResponseEntity<?> sendFile(
			HttpServletRequest httpServletRequest,
			@PathVariable(CLOUD_ID) final String cloudId,
			@PathVariable(REPRESENTATION_NAME) final String representationName,
			@PathVariable(VERSION) final String version,
			@RequestParam String mimeType,
			@RequestParam byte[] data,
			@RequestParam(required = false) String fileName) throws IOException, RepresentationNotExistsException,
											CannotModifyPersistentRepresentationException, FileAlreadyExistsException {

		File f = new File();
		f.setMimeType(mimeType);
		PreBufferedInputStream prebufferedInputStream = wrap(data, objectStoreSizeThreshold);
		f.setFileStorage(new StorageSelector(prebufferedInputStream, mimeType).selectStorage());
		if (fileName != null) {
			try {
				File temp = recordService.getFile(cloudId, representationName, version, fileName);
				if (temp != null) {
					throw new FileAlreadyExistsException(fileName);
				}
			} catch (FileNotExistsException e) {
				// file does not exist, so continue and add it
			}
		}

		if (fileName == null) {
			fileName = UUID.randomUUID().toString();
		}
		f.setFileName(fileName);

		recordService.putContent(cloudId, representationName, version, f, prebufferedInputStream);
		IOUtils.closeQuietly(prebufferedInputStream);
		EnrichUriUtil.enrich(httpServletRequest, cloudId, representationName, version, f);
		LOGGER.debug(String.format("File added [%s, %s, %s], uri: %s ",
				cloudId, representationName, version, f.getContentUri()));

		return ResponseEntity
				.created(f.getContentUri())
				.eTag(f.getMd5())
				.build();
	}

}
