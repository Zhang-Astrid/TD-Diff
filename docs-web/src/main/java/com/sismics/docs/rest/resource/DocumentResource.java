package com.sismics.docs.rest.resource;

import com.google.common.collect.Lists;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.ContributorDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.RelationDao;
import com.sismics.docs.core.dao.RouteStepDao;
import com.sismics.docs.core.dao.TagDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.criteria.DocumentCriteria;
import com.sismics.docs.core.dao.criteria.TagCriteria;
import com.sismics.docs.core.dao.dto.AclDto;
import com.sismics.docs.core.dao.dto.ContributorDto;
import com.sismics.docs.core.dao.dto.DocumentDto;
import com.sismics.docs.core.dao.dto.RelationDto;
import com.sismics.docs.core.dao.dto.RouteStepDto;
import com.sismics.docs.core.dao.dto.TagDto;
import com.sismics.docs.core.event.DocumentCreatedAsyncEvent;
import com.sismics.docs.core.event.DocumentDeletedAsyncEvent;
import com.sismics.docs.core.event.DocumentUpdatedAsyncEvent;
import com.sismics.docs.core.event.FileDeletedAsyncEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.service.TranslationService;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.DocumentUtil;
import com.sismics.docs.core.util.FileUtil;
import com.sismics.docs.core.util.MetadataUtil;
import com.sismics.docs.core.util.PdfUtil;
import com.sismics.docs.core.util.jpa.PaginatedList;
import com.sismics.docs.core.util.jpa.PaginatedLists;
import com.sismics.docs.core.util.jpa.SortCriteria;
import com.sismics.docs.rest.util.DocumentSearchCriteriaUtil;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.AclUtil;
import com.sismics.rest.util.RestUtil;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.util.EmailUtil;
import com.sismics.util.JsonUtil;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.mime.MimeType;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import org.json.JSONObject;
import org.json.JSONArray;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import com.sismics.docs.core.util.EncryptionUtil;
import org.json.JSONException;
import java.util.stream.Collectors;
import java.io.ByteArrayInputStream;

/**
 * Document REST resources.
 *
 * @author bgamard
 */
@Path("/document")
public class DocumentResource extends BaseResource {
    private static final Logger log = LoggerFactory.getLogger(DocumentResource.class);

    private static final String OCR_API_KEY = "K86781666988957";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB for free tier
    private static final String BAIDU_APP_ID = "20250513002355906";
    private static final String BAIDU_SECRET_KEY = "XgMt3CWruNY_ycgmGTh0";
    private static final String BAIDU_TRANSLATE_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    /**
     * Returns a document.
     *
     * @api {get} /document/:id Get a document
     * @apiName GetDocument
     * @apiGroup Document
     * @apiParam {String} id Document ID
     * @apiParam {String} [share] Share ID
     * @apiParam {Boolean} [files] If true includes files information
     * @apiSuccess {String} id ID
     * @apiSuccess {String} title Title
     * @apiSuccess {String} description Description
     * @apiSuccess {Number} create_date Create date (timestamp)
     * @apiSuccess {Number} update_date Update date (timestamp)
     * @apiSuccess {String} language Language
     * @apiSuccess {Boolean} shared True if the document is shared
     * @apiSuccess {Number} file_count Number of files in this document
     * @apiSuccess {Object[]} tags List of tags
     * @apiSuccess {String} tags.id ID
     * @apiSuccess {String} tags.name Name
     * @apiSuccess {String} tags.color Color
     * @apiSuccess {String} subject Subject
     * @apiSuccess {String} identifier Identifier
     * @apiSuccess {String} publisher Publisher
     * @apiSuccess {String} format Format
     * @apiSuccess {String} source Source
     * @apiSuccess {String} type Type
     * @apiSuccess {String} coverage Coverage
     * @apiSuccess {String} rights Rights
     * @apiSuccess {String} creator Username of the creator
     * @apiSuccess {String} file_id Main file ID
     * @apiSuccess {Boolean} writable True if the document is writable by the current user
     * @apiSuccess {Object[]} acls List of ACL
     * @apiSuccess {String} acls.id ID
     * @apiSuccess {String="READ","WRITE"} acls.perm Permission
     * @apiSuccess {String} acls.name Target name
     * @apiSuccess {String="USER","GROUP","SHARE"} acls.type Target type
     * @apiSuccess {Object[]} inherited_acls List of ACL not directly applied to this document
     * @apiSuccess {String="READ","WRITE"} inherited_acls.perm Permission
     * @apiSuccess {String} inherited_acls.source_id Source ID
     * @apiSuccess {String} inherited_acls.source_name Source name
     * @apiSuccess {String} inherited_acls.source_color The color of the Source
     * @apiSuccess {String} inherited_acls.id ID
     * @apiSuccess {String} inherited_acls.name Target name
     * @apiSuccess {String="USER","GROUP","SHARE"} inherited_acls.type Target type
     * @apiSuccess {Object[]} contributors List of users having contributed to this document
     * @apiSuccess {String} contributors.username Username
     * @apiSuccess {String} contributors.email E-mail
     * @apiSuccess {Object[]} relations List of document related to this one
     * @apiSuccess {String} relations.id ID
     * @apiSuccess {String} relations.title Title
     * @apiSuccess {String} relations.source True if this document is the source of the relation
     * @apiSuccess {Object} route_step The current active route step
     * @apiSuccess {String} route_step.name Route step name
     * @apiSuccess {String="APPROVE", "VALIDATE"} route_step.type Route step type
     * @apiSuccess {Boolean} route_step.transitionable True if the route step is actionable by the current user
     * @apiSuccess {Object[]} files List of files
     * @apiSuccess {String} files.id ID
     * @apiSuccess {String} files.name File name
     * @apiSuccess {String} files.version Zero-based version number
     * @apiSuccess {String} files.mimetype MIME type
     * @apiSuccess {String} files.create_date Create date (timestamp)
     * @apiSuccess {Object[]} metadata List of metadata
     * @apiSuccess {String} metadata.id ID
     * @apiSuccess {String} metadata.name Name
     * @apiSuccess {String="STRING","INTEGER","FLOAT","DATE","BOOLEAN"} metadata.type Type
     * @apiSuccess {Object} metadata.value Value
     * @apiError (client) NotFound Document not found
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param shareId Share ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}")
    public Response get(
            @PathParam("id") String documentId,
            @QueryParam("share") String shareId,
            @QueryParam("files") Boolean files) {
        authenticate();

        DocumentDao documentDao = new DocumentDao();
        DocumentDto documentDto = documentDao.getDocument(documentId, PermType.READ, getTargetIdList(shareId));
        if (documentDto == null) {
            throw new NotFoundException();
        }

        JsonObjectBuilder document = createDocumentObjectBuilder(documentDto)
                .add("creator", documentDto.getCreator())
                .add("coverage", JsonUtil.nullable(documentDto.getCoverage()))
                .add("file_count", documentDto.getFileCount())
                .add("format", JsonUtil.nullable(documentDto.getFormat()))
                .add("identifier", JsonUtil.nullable(documentDto.getIdentifier()))
                .add("publisher", JsonUtil.nullable(documentDto.getPublisher()))
                .add("rights", JsonUtil.nullable(documentDto.getRights()))
                .add("source", JsonUtil.nullable(documentDto.getSource()))
                .add("subject", JsonUtil.nullable(documentDto.getSubject()))
                .add("type", JsonUtil.nullable(documentDto.getType()));

        List<TagDto> tagDtoList = null;
        if (principal.isAnonymous()) {
            // No tags in anonymous mode (sharing)
            document.add("tags", Json.createArrayBuilder());
        } else {
            // Add tags visible by the current user on this document
            TagDao tagDao = new TagDao();
            tagDtoList = tagDao.findByCriteria(
                    new TagCriteria()
                            .setTargetIdList(getTargetIdList(null)) // No tags for shares
                            .setDocumentId(documentId),
                    new SortCriteria(1, true));
            document.add("tags", createTagsArrayBuilder(tagDtoList));
        }

        // Add ACL
        AclUtil.addAcls(document, documentId, getTargetIdList(shareId));

        // Add computed ACL
        if (tagDtoList != null) {
            JsonArrayBuilder aclList = Json.createArrayBuilder();
            for (TagDto tagDto : tagDtoList) {
                AclDao aclDao = new AclDao();
                List<AclDto> aclDtoList = aclDao.getBySourceId(tagDto.getId(), AclType.USER);
                for (AclDto aclDto : aclDtoList) {
                    aclList.add(Json.createObjectBuilder()
                            .add("perm", aclDto.getPerm().name())
                            .add("source_id", tagDto.getId())
                            .add("source_name", tagDto.getName())
                            .add("source_color", tagDto.getColor())
                            .add("id", aclDto.getTargetId())
                            .add("name", JsonUtil.nullable(aclDto.getTargetName()))
                            .add("type", aclDto.getTargetType()));
                }
            }
            document.add("inherited_acls", aclList);
        }

        // Add contributors
        ContributorDao contributorDao = new ContributorDao();
        List<ContributorDto> contributorDtoList = contributorDao.getByDocumentId(documentId);
        JsonArrayBuilder contributorList = Json.createArrayBuilder();
        for (ContributorDto contributorDto : contributorDtoList) {
            contributorList.add(Json.createObjectBuilder()
                    .add("username", contributorDto.getUsername())
                    .add("email", contributorDto.getEmail()));
        }
        document.add("contributors", contributorList);

        // Add relations
        RelationDao relationDao = new RelationDao();
        List<RelationDto> relationDtoList = relationDao.getByDocumentId(documentId);
        JsonArrayBuilder relationList = Json.createArrayBuilder();
        for (RelationDto relationDto : relationDtoList) {
            relationList.add(Json.createObjectBuilder()
                    .add("id", relationDto.getId())
                    .add("title", relationDto.getTitle())
                    .add("source", relationDto.isSource()));
        }
        document.add("relations", relationList);

        // Add current route step
        RouteStepDto routeStepDto = new RouteStepDao().getCurrentStep(documentId);
        if (routeStepDto != null && !principal.isAnonymous()) {
            JsonObjectBuilder step = routeStepDto.toJson();
            step.add("transitionable", getTargetIdList(null).contains(routeStepDto.getTargetId()));
            document.add("route_step", step);
        }

        // Add custom metadata
        MetadataUtil.addMetadata(document, documentId);

        // Add files
        if (Boolean.TRUE == files) {
            FileDao fileDao = new FileDao();
            List<File> fileList = fileDao.getByDocumentsIds(Collections.singleton(documentId));

            JsonArrayBuilder filesArrayBuilder = Json.createArrayBuilder();
            for (File fileDb : fileList) {
                filesArrayBuilder.add(RestUtil.fileToJsonObjectBuilder(fileDb));
            }

            document.add("files", filesArrayBuilder);
        }

        return Response.ok().entity(document.build()).build();
    }

    /**
     * Export a document to PDF.
     *
     * @api {get} /document/:id/pdf Export a document to PDF
     * @apiName GetDocumentPdf
     * @apiGroup Document
     * @apiParam {String} id Document ID
     * @apiParam {String} share Share ID
     * @apiParam {Boolean} metadata If true, export metadata
     * @apiParam {Boolean} fitimagetopage If true, fit the images to pages
     * @apiParam {Number} margin Margin around the pages, in millimeter
     * @apiSuccess {String} pdf The whole response is the PDF file
     * @apiError (client) NotFound Document not found
     * @apiError (client) ValidationError Validation error
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param shareId Share ID
     * @param metadata Export metadata
     * @param fitImageToPage Fit images to page
     * @param marginStr Margins
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}/pdf")
    public Response getPdf(
            @PathParam("id") String documentId,
            @QueryParam("share") String shareId,
            final @QueryParam("metadata") Boolean metadata,
            final @QueryParam("fitimagetopage") Boolean fitImageToPage,
            @QueryParam("margin") String marginStr) {
        authenticate();

        // Validate input
        final int margin = ValidationUtil.validateInteger(marginStr, "margin");

        // Get document and check read permission
        DocumentDao documentDao = new DocumentDao();
        final DocumentDto documentDto = documentDao.getDocument(documentId, PermType.READ, getTargetIdList(shareId));
        if (documentDto == null) {
            throw new NotFoundException();
        }

        // Get files
        FileDao fileDao = new FileDao();
        UserDao userDao = new UserDao();
        final List<File> fileList = fileDao.getByDocumentId(null, documentId);
        for (File file : fileList) {
            // A file is always encrypted by the creator of it
            // Store its private key to decrypt it
            User user = userDao.getById(file.getUserId());
            file.setPrivateKey(user.getPrivateKey());
        }

        // Convert to PDF
        StreamingOutput stream = outputStream -> {
            try {
                PdfUtil.convertToPdf(documentDto, fileList, fitImageToPage, metadata, margin, outputStream);
            } catch (Exception e) {
                throw new IOException(e);
            }
        };

        return Response.ok(stream)
                .header("Content-Type", MimeType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + documentDto.getTitle() + ".pdf\"")
                .build();
    }

    /**
     * Returns all documents, if a parameter is considered invalid, the search result will be empty.
     *
     * @api {get} /document/list Get documents
     * @apiName GetDocumentList
     * @apiGroup Document
     *
     * @apiParam {String} [limit] Total number of documents to return (default is <code>10</code>)
     * @apiParam {String} [offset] Start at this index (default is <code>0</code>)
     * @apiParam {Number} [sort_column] Column index to sort on
     * @apiParam {Boolean} [asc] If <code>true</code> sorts in ascending order
     * @apiParam {String} [search] Search query (see "Document search syntax" on the top of the page for explanations) when the input is entered by a human.
     * @apiParam {Boolean} [files] If <code>true</code> includes files information
     *
     * @apiParam {String} [search[after]] The document must have been created after or at the value moment, accepted format is <code>yyyy-MM-dd</code>
     * @apiParam {String} [search[before]] The document must have been created before or at the value moment, accepted format is <code>yyyy-MM-dd</code>
     * @apiParam {String} [search[by]] The document must have been created by the specified creator's username with an exact match, the user must not be deleted
     * @apiParam {String} [search[full]] Used as a search criteria for all fields including the document's files content, several comma-separated values can be specified and the document must match any of them
     * @apiParam {String} [search[lang]] The document must be of the specified language (example: <code>en</code>)
     * @apiParam {String} [search[mime]] The document must be of the specified mime type (example: <code>image/png</code>)
     * @apiParam {String} [search[simple]] Used as a search criteria for all fields except the document's files content, several comma-separated values can be specified and the document must match any of them
     * @apiParam {Boolean} [search[shared]] If <code>true</code> the document must be shared, else it is ignored
     * @apiParam {String} [search[tag]] The document must contain a tag or a child of a tag that starts with the value, case is ignored, several comma-separated values can be specified and the document must match all tag filters
     * @apiParam {String} [search[nottag]] The document must not contain a tag or a child of a tag that starts with the value, case is ignored, several comma-separated values can be specified and the document must match all tag filters
     * @apiParam {String} [search[title]] The document's title must be the value, several comma-separated values can be specified and the document must match any of the titles
     * @apiParam {String} [search[uafter]] The document must have been updated after or at the value moment, accepted format is <code>yyyy-MM-dd</code>
     * @apiParam {String} [search[ubefore]] The document must have been updated before or at the value moment, accepted format is <code>yyyy-MM-dd</code>
     * @apiParam {String} [search[workflow]] If the value is <code>me</code> the document must have an active route, for other values the criteria is ignored
     *
     * @apiSuccess {Number} total Total number of documents
     * @apiSuccess {Object[]} documents List of documents
     * @apiSuccess {String} documents.id ID
     * @apiSuccess {String} documents.highlight Search highlight (for fulltext search)
     * @apiSuccess {String} documents.file_id Main file ID
     * @apiSuccess {String} documents.title Title
     * @apiSuccess {String} documents.description Description
     * @apiSuccess {Number} documents.create_date Create date (timestamp)
     * @apiSuccess {Number} documents.update_date Update date (timestamp)
     * @apiSuccess {String} documents.language Language
     * @apiSuccess {Boolean} documents.shared True if the document is shared
     * @apiSuccess {Boolean} documents.active_route True if a route is active on this document
     * @apiSuccess {Boolean} documents.current_step_name Name of the current route step
     * @apiSuccess {Number} documents.file_count Number of files in this document
     * @apiSuccess {Object[]} documents.tags List of tags
     * @apiSuccess {String} documents.tags.id ID
     * @apiSuccess {String} documents.tags.name Name
     * @apiSuccess {String} documents.tags.color Color
     * @apiSuccess {Object[]} documents.files List of files
     * @apiSuccess {String} documents.files.id ID
     * @apiSuccess {String} documents.files.name File name
     * @apiSuccess {String} documents.files.version Zero-based version number
     * @apiSuccess {String} documents.files.mimetype MIME type
     * @apiSuccess {String} documents.files.create_date Create date (timestamp)
     * @apiSuccess {String[]} suggestions List of search suggestions
     *
     * @apiError (client) ForbiddenError Access denied
     * @apiError (server) SearchError Error searching in documents
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param limit Page limit
     * @param offset Page offset
     * @param sortColumn Sort column
     * @param asc Sorting
     * @param search Search query
     * @param files Files list
     * @return Response
     */
    @GET
    @Path("list")
    public Response list(
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort_column") Integer sortColumn,
            @QueryParam("asc") Boolean asc,
            @QueryParam("search") String search,
            @QueryParam("files") Boolean files,

            @QueryParam("search[after]") String searchCreatedAfter,
            @QueryParam("search[before]") String searchCreatedBefore,
            @QueryParam("search[by]") String searchBy,
            @QueryParam("search[full]") String searchFull,
            @QueryParam("search[lang]") String searchLang,
            @QueryParam("search[mime]") String searchMime,
            @QueryParam("search[shared]") Boolean searchShared,
            @QueryParam("search[simple]") String searchSimple,
            @QueryParam("search[tag]") String searchTag,
            @QueryParam("search[nottag]") String searchTagNot,
            @QueryParam("search[title]") String searchTitle,
            @QueryParam("search[uafter]") String searchUpdatedAfter,
            @QueryParam("search[ubefore]") String searchUpdatedBefore,
            @QueryParam("search[searchworkflow]") String searchWorkflow
    ) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        JsonObjectBuilder response = Json.createObjectBuilder();
        JsonArrayBuilder documents = Json.createArrayBuilder();

        TagDao tagDao = new TagDao();
        PaginatedList<DocumentDto> paginatedList = PaginatedLists.create(limit, offset);
        List<String> suggestionList = Lists.newArrayList();
        SortCriteria sortCriteria = new SortCriteria(sortColumn, asc);

        List<TagDto> allTagDtoList = tagDao.findByCriteria(new TagCriteria().setTargetIdList(getTargetIdList(null)), null);

        DocumentCriteria documentCriteria = DocumentSearchCriteriaUtil.parseSearchQuery(search, allTagDtoList);
        DocumentSearchCriteriaUtil.addHttpSearchParams(
                documentCriteria,
                searchBy,
                searchCreatedAfter,
                searchCreatedBefore,
                searchFull,
                searchLang,
                searchMime,
                searchShared,
                searchSimple,
                searchTag,
                searchTagNot,
                searchTitle,
                searchUpdatedAfter,
                searchUpdatedBefore,
                searchWorkflow,
                allTagDtoList);

        documentCriteria.setTargetIdList(getTargetIdList(null));
        try {
            AppContext.getInstance().getIndexingHandler().findByCriteria(paginatedList, suggestionList, documentCriteria, sortCriteria);
        } catch (Exception e) {
            throw new ServerException("SearchError", "Error searching in documents", e);
        }

        // Find the files of the documents
        Iterable<String> documentsIds = CollectionUtils.collect(paginatedList.getResultList(), DocumentDto::getId);
        FileDao fileDao = new FileDao();
        List<File> filesList = null;
        Map<String, Long> filesCountByDocument = null;
        if (Boolean.TRUE == files) {
            filesList = fileDao.getByDocumentsIds(documentsIds);
        } else {
            filesCountByDocument = fileDao.countByDocumentsIds(documentsIds);
        }

        for (DocumentDto documentDto : paginatedList.getResultList()) {
            // Get tags accessible by the current user on this document
            List<TagDto> tagDtoList = tagDao.findByCriteria(new TagCriteria()
                    .setTargetIdList(getTargetIdList(null))
                    .setDocumentId(documentDto.getId()), new SortCriteria(1, true));

            Long filesCount;
            Collection<File> filesOfDocument = null;
            if (Boolean.TRUE == files) {
                // Find files matching the document
                filesOfDocument = CollectionUtils.select(filesList, file -> file.getDocumentId().equals(documentDto.getId()));
                filesCount = (long) filesOfDocument.size();
            } else {
                filesCount = filesCountByDocument.getOrDefault(documentDto.getId(), 0L);
            }

            JsonObjectBuilder documentObjectBuilder = createDocumentObjectBuilder(documentDto)
                    .add("active_route", documentDto.isActiveRoute())
                    .add("current_step_name", JsonUtil.nullable(documentDto.getCurrentStepName()))
                    .add("highlight", JsonUtil.nullable(documentDto.getHighlight()))
                    .add("file_count", filesCount)
                    .add("tags", createTagsArrayBuilder(tagDtoList));

            if (Boolean.TRUE == files) {
                JsonArrayBuilder filesArrayBuilder = Json.createArrayBuilder();
                for (File fileDb : filesOfDocument) {
                    filesArrayBuilder.add(RestUtil.fileToJsonObjectBuilder(fileDb));
                }
                documentObjectBuilder.add("files", filesArrayBuilder);
            }
            documents.add(documentObjectBuilder);
        }

        JsonArrayBuilder suggestions = Json.createArrayBuilder();
        for (String suggestion : suggestionList) {
            suggestions.add(suggestion);
        }

        response.add("total", paginatedList.getResultCount())
                .add("documents", documents)
                .add("suggestions", suggestions);

        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns all documents.
     *
     * @api {post} /document/list Get documents
     * @apiDescription Get documents exposed as a POST endpoint to allow longer search parameters, see the GET endpoint for the API info
     * @apiName PostDocumentList
     * @apiGroup Document
     * @apiVersion 1.12.0
     *
     * @param limit      Page limit
     * @param offset     Page offset
     * @param sortColumn Sort column
     * @param asc        Sorting
     * @param search     Search query
     * @param files      Files list
     * @return Response
     */
    @POST
    @Path("list")
    public Response listPost(
            @FormParam("limit") Integer limit,
            @FormParam("offset") Integer offset,
            @FormParam("sort_column") Integer sortColumn,
            @FormParam("asc") Boolean asc,
            @FormParam("search") String search,
            @FormParam("files") Boolean files,
            @FormParam("search[after]") String searchCreatedAfter,
            @FormParam("search[before]") String searchCreatedBefore,
            @FormParam("search[by]") String searchBy,
            @FormParam("search[full]") String searchFull,
            @FormParam("search[lang]") String searchLang,
            @FormParam("search[mime]") String searchMime,
            @FormParam("search[shared]") Boolean searchShared,
            @FormParam("search[simple]") String searchSimple,
            @FormParam("search[tag]") String searchTag,
            @FormParam("search[nottag]") String searchTagNot,
            @FormParam("search[title]") String searchTitle,
            @FormParam("search[uafter]") String searchUpdatedAfter,
            @FormParam("search[ubefore]") String searchUpdatedBefore,
            @FormParam("search[searchworkflow]") String searchWorkflow
    ) {
        return list(
                limit,
                offset,
                sortColumn,
                asc,
                search,
                files,
                searchCreatedAfter,
                searchCreatedBefore,
                searchBy,
                searchFull,
                searchLang,
                searchMime,
                searchShared,
                searchSimple,
                searchTag,
                searchTagNot,
                searchTitle,
                searchUpdatedAfter,
                searchUpdatedBefore,
                searchWorkflow
        );
    }

    /**
     * Creates a new document.
     *
     * @api {put} /document Add a document
     * @apiName PutDocument
     * @apiGroup Document
     * @apiParam {String} title Title
     * @apiParam {String} [description] Description
     * @apiParam {String} [subject] Subject
     * @apiParam {String} [identifier] Identifier
     * @apiParam {String} [publisher] Publisher
     * @apiParam {String} [format] Format
     * @apiParam {String} [source] Source
     * @apiParam {String} [type] Type
     * @apiParam {String} [coverage] Coverage
     * @apiParam {String} [rights] Rights
     * @apiParam {String[]} [tags] List of tags ID
     * @apiParam {String[]} [relations] List of related documents ID
     * @apiParam {String[]} [metadata_id] List of metadata ID
     * @apiParam {String[]} [metadata_value] List of metadata values
     * @apiParam {String} language Language
     * @apiParam {Number} [create_date] Create date (timestamp)
     * @apiSuccess {String} id Document ID
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param title Title
     * @param description Description
     * @param subject Subject
     * @param identifier Identifier
     * @param publisher Publisher
     * @param format Format
     * @param source Source
     * @param type Type
     * @param coverage Coverage
     * @param rights Rights
     * @param tagList Tags
     * @param relationList Relations
     * @param metadataIdList Metadata ID list
     * @param metadataValueList Metadata value list
     * @param language Language
     * @param createDateStr Creation date
     * @return Response
     */
    @PUT
    public Response add(
            @FormParam("title") String title,
            @FormParam("description") String description,
            @FormParam("subject") String subject,
            @FormParam("identifier") String identifier,
            @FormParam("publisher") String publisher,
            @FormParam("format") String format,
            @FormParam("source") String source,
            @FormParam("type") String type,
            @FormParam("coverage") String coverage,
            @FormParam("rights") String rights,
            @FormParam("tags") List<String> tagList,
            @FormParam("relations") List<String> relationList,
            @FormParam("metadata_id") List<String> metadataIdList,
            @FormParam("metadata_value") List<String> metadataValueList,
            @FormParam("language") String language,
            @FormParam("create_date") String createDateStr) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate input data
        title = ValidationUtil.validateLength(title, "title", 1, 100, false);
        language = ValidationUtil.validateLength(language, "language", 3, 7, false);
        description = ValidationUtil.validateLength(description, "description", 0, 4000, true);
        subject = ValidationUtil.validateLength(subject, "subject", 0, 500, true);
        identifier = ValidationUtil.validateLength(identifier, "identifier", 0, 500, true);
        publisher = ValidationUtil.validateLength(publisher, "publisher", 0, 500, true);
        format = ValidationUtil.validateLength(format, "format", 0, 500, true);
        source = ValidationUtil.validateLength(source, "source", 0, 500, true);
        type = ValidationUtil.validateLength(type, "type", 0, 100, true);
        coverage = ValidationUtil.validateLength(coverage, "coverage", 0, 100, true);
        rights = ValidationUtil.validateLength(rights, "rights", 0, 100, true);
        Date createDate = ValidationUtil.validateDate(createDateStr, "create_date", true);
        if (!Constants.SUPPORTED_LANGUAGES.contains(language)) {
            throw new ClientException("ValidationError", MessageFormat.format("{0} is not a supported language", language));
        }

        // Create the document
        Document document = new Document();
        document.setUserId(principal.getId());
        document.setTitle(title);
        document.setDescription(description);
        document.setSubject(subject);
        document.setIdentifier(identifier);
        document.setPublisher(publisher);
        document.setFormat(format);
        document.setSource(source);
        document.setType(type);
        document.setCoverage(coverage);
        document.setRights(rights);
        document.setLanguage(language);
        if (createDate == null) {
            document.setCreateDate(new Date());
        } else {
            document.setCreateDate(createDate);
        }

        // Save the document, create the base ACLs
        document = DocumentUtil.createDocument(document, principal.getId());

        // Update tags
        updateTagList(document.getId(), tagList);

        // Update relations
        updateRelationList(document.getId(), relationList);

        // Update custom metadata
        try {
            MetadataUtil.updateMetadata(document.getId(), metadataIdList, metadataValueList);
        } catch (Exception e) {
            throw new ClientException("ValidationError", e.getMessage());
        }

        // Raise a document created event
        DocumentCreatedAsyncEvent documentCreatedAsyncEvent = new DocumentCreatedAsyncEvent();
        documentCreatedAsyncEvent.setUserId(principal.getId());
        documentCreatedAsyncEvent.setDocumentId(document.getId());
        ThreadLocalContext.get().addAsyncEvent(documentCreatedAsyncEvent);

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("id", document.getId());
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Updates the document.
     *
     * @api {post} /document/:id Update a document
     * @apiName PostDocument
     * @apiGroup Document
     * @apiParam {String} id ID
     * @apiParam {String} title Title
     * @apiParam {String} [description] Description
     * @apiParam {String} [subject] Subject
     * @apiParam {String} [identifier] Identifier
     * @apiParam {String} [publisher] Publisher
     * @apiParam {String} [format] Format
     * @apiParam {String} [source] Source
     * @apiParam {String} [type] Type
     * @apiParam {String} [coverage] Coverage
     * @apiParam {String} [rights] Rights
     * @apiParam {String[]} [tags] List of tags ID
     * @apiParam {String[]} [relations] List of related documents ID
     * @apiParam {String[]} [metadata_id] List of metadata ID
     * @apiParam {String[]} [metadata_value] List of metadata values
     * @apiParam {String} [language] Language
     * @apiParam {Number} [create_date] Create date (timestamp)
     * @apiSuccess {String} id Document ID
     * @apiError (client) ForbiddenError Access denied or document not writable
     * @apiError (client) ValidationError Validation error
     * @apiError (client) NotFound Document not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param title Title
     * @param description Description
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}")
    public Response update(
            @PathParam("id") String id,
            @FormParam("title") String title,
            @FormParam("description") String description,
            @FormParam("subject") String subject,
            @FormParam("identifier") String identifier,
            @FormParam("publisher") String publisher,
            @FormParam("format") String format,
            @FormParam("source") String source,
            @FormParam("type") String type,
            @FormParam("coverage") String coverage,
            @FormParam("rights") String rights,
            @FormParam("tags") List<String> tagList,
            @FormParam("relations") List<String> relationList,
            @FormParam("metadata_id") List<String> metadataIdList,
            @FormParam("metadata_value") List<String> metadataValueList,
            @FormParam("language") String language,
            @FormParam("create_date") String createDateStr) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate input data
        title = ValidationUtil.validateLength(title, "title", 1, 100, false);
        language = ValidationUtil.validateLength(language, "language", 3, 7, false);
        description = ValidationUtil.validateLength(description, "description", 0, 4000, true);
        subject = ValidationUtil.validateLength(subject, "subject", 0, 500, true);
        identifier = ValidationUtil.validateLength(identifier, "identifier", 0, 500, true);
        publisher = ValidationUtil.validateLength(publisher, "publisher", 0, 500, true);
        format = ValidationUtil.validateLength(format, "format", 0, 500, true);
        source = ValidationUtil.validateLength(source, "source", 0, 500, true);
        type = ValidationUtil.validateLength(type, "type", 0, 100, true);
        coverage = ValidationUtil.validateLength(coverage, "coverage", 0, 100, true);
        rights = ValidationUtil.validateLength(rights, "rights", 0, 100, true);
        Date createDate = ValidationUtil.validateDate(createDateStr, "create_date", true);
        if (language != null && !Constants.SUPPORTED_LANGUAGES.contains(language)) {
            throw new ClientException("ValidationError", MessageFormat.format("{0} is not a supported language", language));
        }

        // Check write permission
        AclDao aclDao = new AclDao();
        if (!aclDao.checkPermission(id, PermType.WRITE, getTargetIdList(null))) {
            throw new ForbiddenClientException();
        }

        // Get the document
        DocumentDao documentDao = new DocumentDao();
        Document document = documentDao.getById(id);
        if (document == null) {
            throw new NotFoundException();
        }

        // Update the document
        document.setTitle(title);
        document.setDescription(description);
        document.setSubject(subject);
        document.setIdentifier(identifier);
        document.setPublisher(publisher);
        document.setFormat(format);
        document.setSource(source);
        document.setType(type);
        document.setCoverage(coverage);
        document.setRights(rights);
        document.setLanguage(language);
        if (createDate == null) {
            document.setCreateDate(new Date());
        } else {
            document.setCreateDate(createDate);
        }

        documentDao.update(document, principal.getId());

        // Update tags
        updateTagList(id, tagList);

        // Update relations
        updateRelationList(id, relationList);

        // Update custom metadata
        try {
            MetadataUtil.updateMetadata(document.getId(), metadataIdList, metadataValueList);
        } catch (Exception e) {
            throw new ClientException("ValidationError", e.getMessage());
        }

        // Raise a document updated event
        DocumentUpdatedAsyncEvent documentUpdatedAsyncEvent = new DocumentUpdatedAsyncEvent();
        documentUpdatedAsyncEvent.setUserId(principal.getId());
        documentUpdatedAsyncEvent.setDocumentId(id);
        ThreadLocalContext.get().addAsyncEvent(documentUpdatedAsyncEvent);

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("id", id);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Import a new document from an EML file.
     *
     * @api {put} /document/eml Import a new document from an EML file
     * @apiName PutDocumentEml
     * @apiGroup Document
     * @apiParam {String} file File data
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (server) StreamError Error reading the input file
     * @apiError (server) ErrorGuessMime Error guessing mime type
     * @apiError (client) QuotaReached Quota limit reached
     * @apiError (server) FileError Error adding a file
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param fileBodyPart File to import
     * @return Response
     */
    @PUT
    @Path("eml")
    @Consumes("multipart/form-data")
    public Response importEml(@FormDataParam("file") FormDataBodyPart fileBodyPart) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate input data
        ValidationUtil.validateRequired(fileBodyPart, "file");

        // Save the file to a temporary file
        java.nio.file.Path unencryptedFile;
        try {
            unencryptedFile = AppContext.getInstance().getFileService().createTemporaryFile();
            Files.copy(fileBodyPart.getValueAs(InputStream.class), unencryptedFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServerException("StreamError", "Error reading the input file", e);
        }

        // Read the EML file
        Properties props = new Properties();
        Session mailSession = Session.getDefaultInstance(props, null);
        EmailUtil.MailContent mailContent = new EmailUtil.MailContent();
        try (InputStream inputStream = Files.newInputStream(unencryptedFile)) {
            Message message = new MimeMessage(mailSession, inputStream);
            mailContent.setSubject(message.getSubject());
            mailContent.setDate(message.getSentDate());
            EmailUtil.parseMailContent(message, mailContent);
        } catch (IOException | MessagingException e) {
            throw new ServerException("StreamError", "Error reading the temporary file", e);
        }

        // Create the document
        Document document = new Document();
        document.setUserId(principal.getId());
        if (mailContent.getSubject() == null) {
            document.setTitle("Imported email from EML file");
        } else {
            document.setTitle(StringUtils.abbreviate(mailContent.getSubject(), 100));
        }
        document.setDescription(StringUtils.abbreviate(mailContent.getMessage(), 4000));
        document.setSubject(StringUtils.abbreviate(mailContent.getSubject(), 500));
        document.setFormat("EML");
        document.setSource("Email");
        document.setLanguage(ConfigUtil.getConfigStringValue(ConfigType.DEFAULT_LANGUAGE));
        if (mailContent.getDate() == null) {
            document.setCreateDate(new Date());
        } else {
            document.setCreateDate(mailContent.getDate());
        }

        // Save the document, create the base ACLs
        DocumentUtil.createDocument(document, principal.getId());

        // Raise a document created event
        DocumentCreatedAsyncEvent documentCreatedAsyncEvent = new DocumentCreatedAsyncEvent();
        documentCreatedAsyncEvent.setUserId(principal.getId());
        documentCreatedAsyncEvent.setDocumentId(document.getId());
        ThreadLocalContext.get().addAsyncEvent(documentCreatedAsyncEvent);

        // Add files to the document
        try {
            for (EmailUtil.FileContent fileContent : mailContent.getFileContentList()) {
                FileUtil.createFile(fileContent.getName(), null, fileContent.getFile(), fileContent.getSize(),
                        document.getLanguage(), principal.getId(), document.getId());
            }
        } catch (IOException e) {
            throw new ClientException(e.getMessage(), e.getMessage(), e);
        } catch (Exception e) {
            throw new ServerException("FileError", "Error adding a file", e);
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("id", document.getId());
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Deletes a document.
     *
     * @api {delete} /document/:id Delete a document
     * @apiName DeleteDocument
     * @apiGroup Document
     * @apiParam {String} id ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Document not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id Document ID
     * @return Response
     */
    @DELETE
    @Path("{id: [a-z0-9\\-]+}")
    public Response delete(
            @PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the document
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();
        AclDao aclDao = new AclDao();
        if (!aclDao.checkPermission(id, PermType.WRITE, getTargetIdList(null))) {
            throw new NotFoundException();
        }
        List<File> fileList = fileDao.getByDocumentId(principal.getId(), id);

        // Delete the document
        documentDao.delete(id, principal.getId());

        for (File file : fileList) {
            // Raise file deleted event
            FileDeletedAsyncEvent fileDeletedAsyncEvent = new FileDeletedAsyncEvent();
            fileDeletedAsyncEvent.setUserId(principal.getId());
            fileDeletedAsyncEvent.setFileId(file.getId());
            fileDeletedAsyncEvent.setFileSize(file.getSize());
            ThreadLocalContext.get().addAsyncEvent(fileDeletedAsyncEvent);
        }

        // Raise a document deleted event
        DocumentDeletedAsyncEvent documentDeletedAsyncEvent = new DocumentDeletedAsyncEvent();
        documentDeletedAsyncEvent.setUserId(principal.getId());
        documentDeletedAsyncEvent.setDocumentId(id);
        ThreadLocalContext.get().addAsyncEvent(documentDeletedAsyncEvent);

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Update tags list on a document.
     *
     * @param documentId Document ID
     * @param tagList Tag ID list
     */
    private void updateTagList(String documentId, List<String> tagList) {
        if (tagList != null) {
            TagDao tagDao = new TagDao();
            Set<String> tagSet = new HashSet<>();
            Set<String> tagIdSet = new HashSet<>();
            List<TagDto> tagDtoList = tagDao.findByCriteria(new TagCriteria().setTargetIdList(getTargetIdList(null)), null);
            for (TagDto tagDto : tagDtoList) {
                tagIdSet.add(tagDto.getId());
            }
            for (String tagId : tagList) {
                if (!tagIdSet.contains(tagId)) {
                    throw new ClientException("TagNotFound", MessageFormat.format("Tag not found: {0}", tagId));
                }
                tagSet.add(tagId);
            }
            tagDao.updateTagList(documentId, tagSet);
        }
    }

    /**
     * Update relations list on a document.
     *
     * @param documentId Document ID
     * @param relationList Relation ID list
     */
    private void updateRelationList(String documentId, List<String> relationList) {
        if (relationList != null) {
            DocumentDao documentDao = new DocumentDao();
            RelationDao relationDao = new RelationDao();
            Set<String> documentIdSet = new HashSet<>();
            for (String targetDocId : relationList) {
                // ACL are not checked, because the editing user is not forced to view the target document
                Document document = documentDao.getById(targetDocId);
                if (document != null && !documentId.equals(targetDocId)) {
                    documentIdSet.add(targetDocId);
                }
            }
            relationDao.updateRelationList(documentId, documentIdSet);
        }
    }

    private JsonObjectBuilder createDocumentObjectBuilder(DocumentDto documentDto) {
        return Json.createObjectBuilder()
                .add("create_date", documentDto.getCreateTimestamp())
                .add("description", JsonUtil.nullable(documentDto.getDescription()))
                .add("file_id", JsonUtil.nullable(documentDto.getFileId()))
                .add("id", documentDto.getId())
                .add("language", documentDto.getLanguage())
                .add("shared", documentDto.getShared())
                .add("title", documentDto.getTitle())
                .add("update_date", documentDto.getUpdateTimestamp());
    }

    private static JsonArrayBuilder createTagsArrayBuilder(List<TagDto> tagDtoList) {
        JsonArrayBuilder tags = Json.createArrayBuilder();
        for (TagDto tagDto : tagDtoList) {
            tags.add(Json.createObjectBuilder()
                    .add("id", tagDto.getId())
                    .add("name", tagDto.getName())
                    .add("color", tagDto.getColor()));
        }
        return tags;
    }

    private String translateWithBaidu(String text, String targetLanguage) throws IOException {
        // 生成随机数
        String salt = String.valueOf(System.currentTimeMillis());
        
        // 计算签名
        String sign = generateBaiduSign(text, salt);
        
        // 构建请求参数
        StringBuilder requestParams = new StringBuilder();
        requestParams.append("q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        requestParams.append("&from=auto"); // 自动检测源语言
        requestParams.append("&to=").append(getBaiduLanguageCode(targetLanguage));
        requestParams.append("&appid=").append(BAIDU_APP_ID);
        requestParams.append("&salt=").append(salt);
        requestParams.append("&sign=").append(sign);
    
        // 发送请求
        URL url = new URL(BAIDU_TRANSLATE_URL + "?" + requestParams.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
    
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Baidu Translate API error: " + responseCode);
        }
    
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
    
            JSONObject jsonResponse = new JSONObject(response.toString());
            if (jsonResponse.has("error_code")) {
                String errorCode = jsonResponse.getString("error_code");
                String errorMsg = jsonResponse.getString("error_msg");
                throw new IOException("Baidu Translate API error: " + errorCode + " - " + errorMsg);
            }
    
            JSONArray transResult = jsonResponse.getJSONArray("trans_result");
            if (transResult.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < transResult.length(); i++) {
                    sb.append(transResult.getJSONObject(i).getString("dst"));
                    if (i < transResult.length() - 1) {
                        sb.append("\n");
                    }
                }
                return sb.toString();
            }
            throw new IOException("No translation result found");
        }
    }
    
    private String generateBaiduSign(String text, String salt) throws IOException {
        try {
            String str = BAIDU_APP_ID + text + salt + BAIDU_SECRET_KEY;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to generate sign", e);
        }
    }
    
    private String getBaiduLanguageCode(String language) {
        // 记录原始传入的语言代码
        log.info("[getBaiduLanguageCode] Original language code: {}", language);
        
        // 前端直接传入的语言代码 (zh, en, ja, ko, fr, de, es, ru)
        if (language == null) return "en";
        
        switch (language.toLowerCase()) {
            // 前端直接传入的语言代码
            case "zh": return "zh";
            case "en": return "en";
            case "ja": return "jp";
            case "ko": return "kor";
            case "fr": return "fra";
            case "de": return "de";
            case "es": return "spa";
            case "ru": return "ru";
            
            // OCR.space 语言代码映射
            case "eng": return "en";
            case "chi_sim": return "zh";
            case "chi_tra": return "cht";
            case "jpn": return "jp";
            case "kor": return "kor";
            case "fra": return "fra";
            case "deu": return "de";
            case "rus": return "ru";
            case "spa": return "spa";
            case "ita": return "it";
            case "por": return "pt";
            case "vie": return "vie";
            case "tur": return "tr";
            case "tha": return "th";
            case "ara": return "ara";
            default:
                log.warn("[getBaiduLanguageCode] Unsupported language code: {}, falling back to English", language);
                return "en"; // 默认使用英语
        }
    }

    @POST
    @Path("{id}/translate")
    public Response translate(
            @PathParam("id") String id,
            @FormParam("targetLanguage") String targetLanguage,
            @FormParam("contentType") String contentType) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        DocumentDao documentDao = new DocumentDao();
        Document document = documentDao.getById(id);
        if (document == null) {
            throw new NotFoundException();
        }
        // 检查权限
        if (!document.hasReadAccess(getTargetIdList(null))) {
            throw new ForbiddenClientException();
        }

        try {
            log.info("[translate] Request received - target language: {}, content type: {}", targetLanguage, contentType);
            String textToTranslate = null;
            log.info("[translate] contentType: {}", contentType);
            if ("description".equals(contentType)) {
                textToTranslate = document.getDescription();
            } else if ("file".equals(contentType)) {
                FileDao fileDao = new FileDao();
                List<File> files = fileDao.getByDocumentId(null, id);
                if (files == null || files.isEmpty()) {
                    log.error("[translate] No file found for document");
                    throw new NotFoundException("No file found for document");
                }
                File file = files.get(0); // 取第一个主文件
                String filePath = file.getPath();
                log.info("[translate] filePath: {}", filePath);
                
                // 使用 ocr.space API 处理文件
                try {
                    // 读取加密的文件内容
                    java.nio.file.Path storedFile = Paths.get(filePath);
                    
                    // 获取文件的 MIME 类型
                    String mimeType = file.getMimeType();
                    log.info("[translate] File MIME type from database: {}", mimeType);
                    
                    // 根据 MIME 类型确定文件类型
                    String fileType = getFileTypeFromMimeType(mimeType);
                    if (fileType == null) {
                        throw new IOException("Unsupported file type: " + mimeType);
                    }
                    
                    // 解密文件内容
                    byte[] decryptedContent;
                    try {
                        // 获取文件创建者的私钥
                        UserDao userDao = new UserDao();
                        User creator = userDao.getById(file.getUserId());
                        if (creator == null) {
                            throw new IOException("File creator not found");
                        }
                        
                        // 使用创建者的私钥解密文件
                        java.nio.file.Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, creator.getPrivateKey());
                        decryptedContent = Files.readAllBytes(unencryptedFile);
                        log.info("[translate] Successfully decrypted file content");
                    } catch (Exception e) {
                        log.error("[translate] Error decrypting file: {}", e.getMessage());
                        throw new IOException("Failed to decrypt file: " + e.getMessage());
                    }
                    
                    textToTranslate = processFileWithOcrSpace(decryptedContent, fileType, targetLanguage);
                } catch (Exception e) {
                    log.error("[translate] Error processing file with OCR.space: {}", e.getMessage());
                    throw new ClientException("FileProcessError", "Error processing file: " + e.getMessage());
                }
            } else {
                log.error("[translate] Invalid contentType: {}", contentType);
                throw new ClientException("InvalidContentType", "Invalid contentType");
            }

            if (textToTranslate == null || textToTranslate.trim().isEmpty()) {
                log.error("[translate] No text to translate");
                throw new ClientException("NoText", "No text to translate");
            }

            // 使用百度翻译 API 进行翻译
            String translatedContent;
            try {
                translatedContent = translateWithBaidu(textToTranslate, targetLanguage);
                log.info("[translate] Successfully translated text with Baidu Translate API");
            } catch (Exception e) {
                log.error("[translate] Error translating with Baidu Translate API: {}", e.getMessage());
                throw new ServerException("TranslationError", "Error translating document", e);
            }

            JsonObjectBuilder response = Json.createObjectBuilder();
            response.add("translated", translatedContent);
            return Response.ok(response.build()).build();
        } catch (Exception e) {
            log.error("[translate] Error translating document: {}", e.getMessage(), e);
            throw new ServerException("TranslationError", "Error translating document", e);
        }
    }

    private String processFileWithOcrSpace(byte[] fileData, String fileType, String targetLanguage) throws IOException {
        // 检查文件大小
        if (fileData.length > MAX_FILE_SIZE) {
            throw new IOException("File size exceeds the limit of 1MB for free tier");
        }

        log.info("[processFileWithOcrSpace] Starting file type detection for file size: {} bytes", fileData.length);
        
        // 检测文件类型
        String detectedFileType = fileType;
        String detectedMimeType = null;
        
        try {
            Tika tika = new Tika();
            detectedMimeType = tika.detect(fileData);
            log.info("[processFileWithOcrSpace] Detected MIME type: {}", detectedMimeType);
            
            // 根据MIME类型确定文件类型
            if (detectedMimeType != null) {
                if (detectedMimeType.startsWith("image/")) {
                    detectedFileType = "image";
                } else if (detectedMimeType.equals("application/pdf")) {
                    detectedFileType = "pdf";
                } else if (detectedMimeType.equals("application/msword") || 
                         detectedMimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                    detectedFileType = "doc";
                }
            }
        } catch (Exception e) {
            log.warn("[processFileWithOcrSpace] Error detecting file type: {}", e.getMessage());
        }

        log.info("[processFileWithOcrSpace] Using file type: {}", detectedFileType);
        
        // 对于docx文件，尝试直接使用Apache POI提取文本
        if ("doc".equals(detectedFileType) || "docx".equals(detectedFileType)) {
            try {
                log.info("[processFileWithOcrSpace] Attempting to extract text directly from docx file");
                String extractedText = extractTextFromDocx(fileData);
                if (extractedText != null && !extractedText.trim().isEmpty()) {
                    log.info("[processFileWithOcrSpace] Successfully extracted text directly from docx: {} characters", extractedText.length());
                    return extractedText;
                } else {
                    log.warn("[processFileWithOcrSpace] Direct text extraction yielded no results, falling back to OCR");
                }
            } catch (Exception e) {
                log.warn("[processFileWithOcrSpace] Error extracting text directly, falling back to OCR: {}", e.getMessage());
            }
        }

        String ocrApiUrl = "https://api.ocr.space/parse/image";
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        String LINE_FEED = "\r\n";
        
        int retryCount = 0;
        IOException lastException = null;
        
        while (retryCount < MAX_RETRIES) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(ocrApiUrl).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("apikey", OCR_API_KEY);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                
                try (OutputStream outputStream = connection.getOutputStream()) {
                    // 文件部分
                    String fileName = "file." + detectedFileType.toLowerCase();
                    StringBuilder sb = new StringBuilder();
                    
                    // 对于docx文件，尝试直接提取文本，不使用OCR
                    if ("doc".equals(detectedFileType) || "docx".equals(detectedFileType)) {
                        try {
                            log.info("[processFileWithOcrSpace] Attempting to extract text directly from docx file");
                            String extractedText = extractTextFromDocx(fileData);
                            if (extractedText != null && !extractedText.trim().isEmpty()) {
                                log.info("[processFileWithOcrSpace] Successfully extracted text directly from docx: {} characters", extractedText.length());
                                return extractedText;
                            } else {
                                log.warn("[processFileWithOcrSpace] Direct text extraction yielded no results, falling back to OCR");
                            }
                        } catch (Exception e) {
                            log.warn("[processFileWithOcrSpace] Error extracting text directly, falling back to OCR: {}", e.getMessage());
                        }
                        
                        // 将Word文档转为PDF模式上传 - OCR.space只接受image/jpg, image/png和application/pdf类型的base64
                        log.info("[processFileWithOcrSpace] Using file upload for docx/doc file with PDF filetype");
                        
                        // 使用普通的文件上传方式
                        sb.append("--").append(boundary).append(LINE_FEED);
                        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"").append(LINE_FEED);
                        // 假装这是PDF文件，因为OCR.space可以更好地处理PDF
                        sb.append("Content-Type: application/pdf").append(LINE_FEED);
                        sb.append(LINE_FEED);
                        outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                        outputStream.write(fileData);
                        outputStream.write(LINE_FEED.getBytes(StandardCharsets.UTF_8));
                        
                        // 指定文件类型为PDF
                        String filetypeParam = "--" + boundary + LINE_FEED +
                                "Content-Disposition: form-data; name=\"filetype\"" + LINE_FEED + LINE_FEED +
                                "PDF" + LINE_FEED;
                        outputStream.write(filetypeParam.getBytes(StandardCharsets.UTF_8));
                    } else if (detectedFileType.equalsIgnoreCase("pdf") || 
                             detectedFileType.equalsIgnoreCase("jpg") || 
                             detectedFileType.equalsIgnoreCase("jpeg") || 
                             detectedFileType.equalsIgnoreCase("png")) {
                        // 这些是OCR.space支持通过base64上传的类型
                        log.info("[processFileWithOcrSpace] Using base64Image mode for {} file", detectedFileType);
                        String base64File = Base64.getEncoder().encodeToString(fileData);
                        String mimeType;
                        if (detectedFileType.equalsIgnoreCase("pdf")) {
                            mimeType = "application/pdf";
                        } else if (detectedFileType.equalsIgnoreCase("jpg") || detectedFileType.equalsIgnoreCase("jpeg")) {
                            mimeType = "image/jpeg";
                        } else {
                            mimeType = "image/png";
                        }
                        log.info("[processFileWithOcrSpace] Using mimetype: {}", mimeType);
                        
                        // base64Image参数 - 严格按照OCR.space需要的格式: data:mimetype;base64,base64content
                        sb.append("--").append(boundary).append(LINE_FEED);
                        sb.append("Content-Disposition: form-data; name=\"base64Image\"").append(LINE_FEED);
                        sb.append(LINE_FEED);
                        sb.append("data:").append(mimeType).append(";base64,").append(base64File).append(LINE_FEED);
                        outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                        log.info("[processFileWithOcrSpace] Base64 data written to request");
                        
                        // 添加filetype参数
                        String filetypeParam = "--" + boundary + LINE_FEED +
                                "Content-Disposition: form-data; name=\"filetype\"" + LINE_FEED + LINE_FEED +
                                detectedFileType.toUpperCase() + LINE_FEED;
                        outputStream.write(filetypeParam.getBytes(StandardCharsets.UTF_8));
                    } else {
                        // 其他类型文件使用file字段上传
                        log.info("[processFileWithOcrSpace] Using file mode for {} file", detectedFileType);
                        sb.append("--").append(boundary).append(LINE_FEED);
                        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"").append(LINE_FEED);
                        String mimeType = detectedMimeType != null ? detectedMimeType : getMimeTypeFromFileType(detectedFileType);
                        sb.append("Content-Type: ").append(mimeType).append(LINE_FEED);
                        sb.append(LINE_FEED);
                        outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                        outputStream.write(fileData);
                        outputStream.write(LINE_FEED.getBytes(StandardCharsets.UTF_8));
                        
                        // filetype - 只在file模式下设置
                        String filetypeParam = "--" + boundary + LINE_FEED +
                                "Content-Disposition: form-data; name=\"filetype\"" + LINE_FEED + LINE_FEED +
                                detectedFileType.toUpperCase() + LINE_FEED;
                        outputStream.write(filetypeParam.getBytes(StandardCharsets.UTF_8));
                    }

                    // 语言参数 - 使用 auto 让 OCR.space 自动检测语言
                    String langParam = "--" + boundary + LINE_FEED +
                            "Content-Disposition: form-data; name=\"language\"" + LINE_FEED + LINE_FEED +
                            "auto" + LINE_FEED;
                    outputStream.write(langParam.getBytes(StandardCharsets.UTF_8));
                    
                    // isOverlayRequired = false
                    String overlayParam = "--" + boundary + LINE_FEED +
                            "Content-Disposition: form-data; name=\"isOverlayRequired\"" + LINE_FEED + LINE_FEED +
                            "false" + LINE_FEED;
                    outputStream.write(overlayParam.getBytes(StandardCharsets.UTF_8));
                    
                    // OCREngine = 2 (更准确的引擎)
                    String engineParam = "--" + boundary + LINE_FEED +
                            "Content-Disposition: form-data; name=\"OCREngine\"" + LINE_FEED + LINE_FEED +
                            "2" + LINE_FEED;
                    outputStream.write(engineParam.getBytes(StandardCharsets.UTF_8));
                    
                    // scale = true (提高识别率)
                    String scaleParam = "--" + boundary + LINE_FEED +
                            "Content-Disposition: form-data; name=\"scale\"" + LINE_FEED + LINE_FEED +
                            "true" + LINE_FEED;
                    outputStream.write(scaleParam.getBytes(StandardCharsets.UTF_8));
                    
                    // detectOrientation = true (自动检测方向)
                    String orientationParam = "--" + boundary + LINE_FEED +
                            "Content-Disposition: form-data; name=\"detectOrientation\"" + LINE_FEED + LINE_FEED +
                            "true" + LINE_FEED;
                    outputStream.write(orientationParam.getBytes(StandardCharsets.UTF_8));
                    
                    // 结束标记
                    String endBoundary = "--" + boundary + "--" + LINE_FEED;
                    outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("OCR.space API error: " + responseCode);
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    String responseStr = response.toString();
                    log.info("[processFileWithOcrSpace] OCR.space response: {}", responseStr);
                    
                    JSONObject jsonResponse = new JSONObject(responseStr);
                    
                    // 首先检查是否有ProcessingTimeInMilliseconds字段，这表示API确实处理了请求
                    if (jsonResponse.has("ProcessingTimeInMilliseconds")) {
                        log.info("[processFileWithOcrSpace] Processing time: {} ms", jsonResponse.getInt("ProcessingTimeInMilliseconds"));
                    }
                    
                    // 检查处理是否出错
                    if (jsonResponse.has("IsErroredOnProcessing")) {
                        boolean isErrored = jsonResponse.getBoolean("IsErroredOnProcessing");
                        log.info("[processFileWithOcrSpace] IsErroredOnProcessing: {}", isErrored);
                        
                        if (!isErrored) {
                            // 成功处理，提取文本
                            if (jsonResponse.has("ParsedResults")) {
                                JSONArray parsedResults = jsonResponse.getJSONArray("ParsedResults");
                                log.info("[processFileWithOcrSpace] ParsedResults count: {}", parsedResults.length());
                                
                                if (parsedResults.length() > 0) {
                                    StringBuilder extractedText = new StringBuilder();
                                    for (int i = 0; i < parsedResults.length(); i++) {
                                        JSONObject result = parsedResults.getJSONObject(i);
                                        if (result.has("ParsedText")) {
                                            String parsedText = result.getString("ParsedText");
                                            log.info("[processFileWithOcrSpace] Page {} parsed text length: {} chars", i+1, parsedText.length());
                                            extractedText.append(parsedText);
                                            if (i < parsedResults.length() - 1) {
                                                extractedText.append("\n");
                                            }
                                        } else {
                                            log.warn("[processFileWithOcrSpace] No ParsedText in result {}", i);
                                        }
                                    }
                                    
                                    String finalText = extractedText.toString();
                                    if (!finalText.trim().isEmpty()) {
                                        log.info("[processFileWithOcrSpace] Successfully extracted text: {} chars", finalText.length());
                                        return finalText;
                                    } else {
                                        log.warn("[processFileWithOcrSpace] Extracted text is empty");
                                    }
                                }
                            } else {
                                log.warn("[processFileWithOcrSpace] No ParsedResults in response");
                            }
                        }
                    }
                    
                    // 处理错误消息，可能是字符串或数组
                    String errorMessage = "OCR.space API error";
                    if (jsonResponse.has("ErrorMessage")) {
                        Object errorObj = jsonResponse.get("ErrorMessage");
                        if (errorObj instanceof String) {
                            // 错误消息是字符串
                            errorMessage = errorMessage + ": " + (String)errorObj;
                            log.error("[processFileWithOcrSpace] Error message (string): {}", (String)errorObj);
                        } else if (errorObj instanceof JSONArray) {
                            // 错误消息是数组
                            JSONArray errorArray = (JSONArray)errorObj;
                            StringBuilder errorBuilder = new StringBuilder(errorMessage + ":");
                            log.error("[processFileWithOcrSpace] Error message (array) with {} items", errorArray.length());
                            
                            for (int i = 0; i < errorArray.length(); i++) {
                                String errorItem = errorArray.getString(i);
                                errorBuilder.append(" ").append(errorItem);
                                log.error("[processFileWithOcrSpace] Error item {}: {}", i, errorItem);
                                
                                if (i < errorArray.length() - 1) {
                                    errorBuilder.append(",");
                                }
                            }
                            errorMessage = errorBuilder.toString();
                        } else {
                            // 未知类型
                            log.error("[processFileWithOcrSpace] Error message is of unexpected type: {}", errorObj.getClass().getName());
                            errorMessage = errorMessage + ": Unknown error type";
                        }
                    } else {
                        log.error("[processFileWithOcrSpace] No ErrorMessage in response");
                    }
                    
                    // 记录OCR退出代码（如果有）
                    if (jsonResponse.has("OCRExitCode")) {
                        log.error("[processFileWithOcrSpace] OCR exit code: {}", jsonResponse.getInt("OCRExitCode"));
                    }
                    
                    throw new IOException(errorMessage);
                }
            } catch (IOException e) {
                lastException = e;
                log.warn("[processFileWithOcrSpace] Attempt {} failed: {}", retryCount + 1, e.getMessage());
                log.error("[processFileWithOcrSpace] Error details:", e);
                retryCount++;
                if (retryCount < MAX_RETRIES) {
                    try {
                        log.info("[processFileWithOcrSpace] Retrying in {} ms...", RETRY_DELAY_MS * (1 << retryCount));
                        Thread.sleep(RETRY_DELAY_MS * (1 << retryCount)); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry", ie);
                    }
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        
        throw new IOException("Failed to process file after " + MAX_RETRIES + " attempts", lastException);
    }
    
    private String extractTextFromDocx(byte[] fileData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData)) {
            XWPFDocument document = new XWPFDocument(bis);
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            String text = extractor.getText();
            extractor.close();
            return text;
        } catch (Exception e) {
            log.error("[extractTextFromDocx] Error: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractTextFromOfficeDocument(byte[] fileData, String fileType) throws IOException {
        try {
            if (fileType.equalsIgnoreCase("docx")) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(fileData))) {
                    XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
                    return extractor.getText();
                }
            } else if (fileType.equalsIgnoreCase("doc")) {
                // 对于旧版Word文档，我们可以尝试使用Apache POI的其他类
                // 这里需要添加相应的代码
                return null;
            }
        } catch (Exception e) {
            log.error("[extractTextFromOfficeDocument] Error extracting text from Office document: {}", e.getMessage());
        }
        return null;
    }

    private String analyzeFileContent(byte[] fileData) {
        // 检查文件头
        if (fileData.length < 8) return null;
        
        // 打印文件头用于调试
        StringBuilder headerHex = new StringBuilder();
        for (int i = 0; i < Math.min(16, fileData.length); i++) {
            headerHex.append(String.format("%02X ", fileData[i]));
        }
        log.info("[analyzeFileContent] File header (hex): {}", headerHex.toString());
        
        // 检查常见的文件头
        if (isPdfHeader(fileData)) return "pdf";
        if (isPngHeader(fileData)) return "png";
        if (isJpegHeader(fileData)) return "jpg";
        if (isTiffHeader(fileData)) return "tiff";
        if (isDocHeader(fileData)) return "doc";
        if (isDocxHeader(fileData)) return "docx";
        if (isXlsHeader(fileData)) return "xls";
        if (isXlsxHeader(fileData)) return "xlsx";
        if (isPptHeader(fileData)) return "ppt";
        if (isPptxHeader(fileData)) return "pptx";
        
        // 检查文本内容
        if (isTextContent(fileData)) {
            // 进一步分析文本内容
            String content = new String(fileData, StandardCharsets.UTF_8);
            if (content.contains("<?xml") || content.contains("<html") || content.contains("<body")) {
                return "txt";
            }
        }
        
        // 如果文件头检测失败，尝试使用更宽松的检测方法
        if (isLikelyPdf(fileData)) return "pdf";
        if (isLikelyImage(fileData)) return "jpg";
        if (isLikelyText(fileData)) return "txt";
        if (isLikelyOfficeDocument(fileData)) return "docx";
        
        return null;
    }

    private boolean isPptHeader(byte[] data) {
        if (data.length < 8 || data[0] != (byte)0xD0 || data[1] != (byte)0xCF || 
            data[2] != (byte)0x11 || data[3] != (byte)0xE0) {
            return false;
        }
        String content = new String(data, StandardCharsets.UTF_8);
        return content.contains("PowerPoint");
    }

    private boolean isPptxHeader(byte[] data) {
        if (data.length < 4 || data[0] != 'P' || data[1] != 'K' || data[2] != 0x03 || data[3] != 0x04) {
            return false;
        }
        String content = new String(data, StandardCharsets.UTF_8);
        return content.contains("ppt/presentation.xml");
    }

    private boolean isTextContent(byte[] fileData) {
        int textChars = 0;
        int totalChars = Math.min(fileData.length, 1000); // 只检查前1000个字节
        
        for (int i = 0; i < totalChars; i++) {
            byte b = fileData[i];
            // 检查是否为可打印ASCII字符、制表符、换行符或回车符
            if ((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13) {
                textChars++;
            }
        }
        
        // 如果80%以上是文本字符，认为是文本文件
        return (double)textChars / totalChars > 0.8;
    }

    private boolean isPdfHeader(byte[] data) {
        return data.length >= 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
    }

    private boolean isPngHeader(byte[] data) {
        return data.length >= 8 && data[0] == (byte)0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }

    private boolean isJpegHeader(byte[] data) {
        return data.length >= 2 && data[0] == (byte)0xFF && data[1] == (byte)0xD8;
    }

    private boolean isTiffHeader(byte[] data) {
        return data.length >= 4 && ((data[0] == 'I' && data[1] == 'I') || (data[0] == 'M' && data[1] == 'M'));
    }

    private boolean isDocHeader(byte[] data) {
        return data.length >= 8 && data[0] == (byte)0xD0 && data[1] == (byte)0xCF && 
               data[2] == (byte)0x11 && data[3] == (byte)0xE0;
    }

    private boolean isDocxHeader(byte[] data) {
        if (data.length < 4 || data[0] != 'P' || data[1] != 'K' || data[2] != 0x03 || data[3] != 0x04) {
            return false;
        }
        String content = new String(data, StandardCharsets.UTF_8);
        return content.contains("word/document.xml");
    }

    private boolean isXlsHeader(byte[] data) {
        if (data.length < 8 || data[0] != (byte)0xD0 || data[1] != (byte)0xCF || 
            data[2] != (byte)0x11 || data[3] != (byte)0xE0) {
            return false;
        }
        String content = new String(data, StandardCharsets.UTF_8);
        return content.contains("Workbook");
    }

    private boolean isXlsxHeader(byte[] data) {
        if (data.length < 4 || data[0] != 'P' || data[1] != 'K' || data[2] != 0x03 || data[3] != 0x04) {
            return false;
        }
        String content = new String(data, StandardCharsets.UTF_8);
        return content.contains("xl/workbook.xml");
    }

    private String detectFileTypeFromContent(byte[] fileData) {
        log.info("[detectFileTypeFromContent] Starting content-based file type detection");
        String fileType = analyzeFileContent(fileData);
        log.info("[detectFileTypeFromContent] Detection result: {}", fileType);
        return fileType;
    }

    private String detectMimeType(byte[] fileData) throws IOException {
        try {
            Tika tika = new Tika();
            String mimeType = tika.detect(fileData);
            log.info("[detectMimeType] Detected MIME type: {}", mimeType);
            return mimeType;
        } catch (Exception e) {
            log.warn("[detectMimeType] Failed to detect MIME type: {}", e.getMessage());
            return null;
        }
    }

    private String getFileTypeFromMimeType(String mimeType) {
        if (mimeType == null) return null;
        
        switch (mimeType.toLowerCase()) {
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/png":
                return "png";
            case "application/pdf":
                return "pdf";
            case "image/tiff":
            case "image/tif":
                return "tiff";
            case "text/plain":
                return "txt";
            case "application/msword":
                return "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return "docx";
            case "application/vnd.ms-excel":
                return "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return "xlsx";
            case "application/vnd.ms-powerpoint":
                return "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
                return "pptx";
            default:
                return null;
        }
    }

    private boolean isTextFile(String fileType) {
        return "txt".equalsIgnoreCase(fileType);
    }

    private boolean isOfficeDocument(String fileType) {
        return fileType != null && (
            fileType.equalsIgnoreCase("doc") ||
            fileType.equalsIgnoreCase("docx") ||
            fileType.equalsIgnoreCase("xls") ||
            fileType.equalsIgnoreCase("xlsx") ||
            fileType.equalsIgnoreCase("ppt") ||
            fileType.equalsIgnoreCase("pptx")
        );
    }

    private String translateWithOcrSpace(String text, String targetLanguage) throws IOException {
        String ocrSpaceLanguage = getOcrSpaceLanguageCode(targetLanguage);
        if (ocrSpaceLanguage == null) {
            throw new IOException("Unsupported target language: " + targetLanguage);
        }

        StringBuilder requestParams = new StringBuilder();
        requestParams.append("apikey=").append(OCR_API_KEY);
        requestParams.append("&language=").append(ocrSpaceLanguage);
        requestParams.append("&text=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));

        log.info("[translateWithOcrSpace] Sending request to OCR.space API with language: {}", ocrSpaceLanguage);

        URL url = new URL("https://api.ocr.space/translate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestParams.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        String response;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
            response = br.lines().collect(Collectors.joining("\n"));
        }

        log.info("[translateWithOcrSpace] Received response with code: {}", responseCode);

        if (responseCode != 200) {
            throw new IOException("OCR.space API error: " + responseCode + " - " + response);
        }

        try {
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("translatedText")) {
                return jsonResponse.getString("translatedText");
            }
            throw new IOException("No translation found in OCR.space response");
        } catch (JSONException e) {
            throw new IOException("Error parsing OCR.space response: " + e.getMessage());
        }
    }

    private String getOcrSpaceLanguageCode(String language) {
        // OCR.space 支持的语言代码映射
        switch (language.toLowerCase()) {
            case "eng":
                return "eng";
            case "chi_sim":
                return "chs";
            case "chi_tra":
                return "cht";
            case "jpn":
                return "jpn";
            case "kor":
                return "kor";
            case "fra":
                return "fre";
            case "deu":
                return "ger";
            case "rus":
                return "rus";
            case "spa":
                return "spa";
            case "ita":
                return "ita";
            case "por":
                return "por";
            case "vie":
                return "vie";
            case "tur":
                return "tur";
            case "tha":
                return "tha";
            case "ara":
                return "ara";
            default:
                return "eng"; // 默认使用英语
        }
    }

    private String getMimeTypeFromFileType(String fileType) {
        switch (fileType.toLowerCase()) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "pdf":
                return "application/pdf";
            case "tiff":
            case "tif":
                return "image/tiff";
            case "txt":
                return "text/plain";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default:
                return "application/octet-stream";
        }
    }

    private boolean isLikelyPdf(byte[] data) {
        // 检查PDF文件头
        if (data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-') {
            return true;
        }
        
        // 检查文件内容中是否包含PDF特征
        String content = new String(data, StandardCharsets.UTF_8);
        return content.contains("%PDF") || content.contains("/Type /Page") || content.contains("/Type /Catalog");
    }

    private boolean isLikelyImage(byte[] data) {
        // 检查常见图片文件头
        if (data.length >= 2) {
            // JPEG
            if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) {
                return true;
            }
            // PNG
            if (data.length >= 8 && data[0] == (byte)0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
                return true;
            }
            // TIFF
            if (data.length >= 4 && ((data[0] == 'I' && data[1] == 'I') || (data[0] == 'M' && data[1] == 'M'))) {
                return true;
            }
        }
        
        // 检查文件内容特征
        int imageBytes = 0;
        int totalBytes = Math.min(data.length, 1000);
        for (int i = 0; i < totalBytes; i++) {
            if ((data[i] & 0xFF) >= 0x20 && (data[i] & 0xFF) <= 0x7E) {
                imageBytes++;
            }
        }
        return (double)imageBytes / totalBytes < 0.3; // 如果可打印字符比例很低，可能是图片
    }

    private boolean isLikelyText(byte[] data) {
        int textChars = 0;
        int totalChars = Math.min(data.length, 1000);
        
        for (int i = 0; i < totalChars; i++) {
            byte b = data[i];
            if ((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13) {
                textChars++;
            }
        }
        
        return (double)textChars / totalChars > 0.8;
    }

    private boolean isLikelyOfficeDocument(byte[] data) {
        // 检查Office文件头
        if (data.length >= 8) {
            // DOC, XLS, PPT
            if (data[0] == (byte)0xD0 && data[1] == (byte)0xCF && data[2] == (byte)0x11 && data[3] == (byte)0xE0) {
                return true;
            }
            // DOCX, XLSX, PPTX
            if (data[0] == 'P' && data[1] == 'K' && data[2] == 0x03 && data[3] == 0x04) {
                return true;
            }
        }
        
        // 检查文件内容特征
        String content = new String(data, StandardCharsets.UTF_8);
        return content.contains("<?xml") || 
               content.contains("<w:document") || 
               content.contains("<xl:workbook") || 
               content.contains("<p:presentation");
    }
}


