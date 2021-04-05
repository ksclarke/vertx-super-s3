
package info.freelibrary.vertx.s3.service;

import java.io.IOException;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.model.S3Object;

import info.freelibrary.vertx.s3.AbstractS3FT;
import info.freelibrary.vertx.s3.AwsCredentials;
import info.freelibrary.vertx.s3.S3ClientOptions;
import info.freelibrary.vertx.s3.S3ObjectData;
import info.freelibrary.vertx.s3.TestConstants;

import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests of the <code>S3ClientService</code>.
 */
@RunWith(VertxUnitRunner.class)
public class S3ClientServiceImplFT extends AbstractS3FT {

    /**
     * Rule that creates the test context.
     */
    @Rule
    public RunTestOnContext myContext = new RunTestOnContext();

    private String myBucket;

    private String myKey;

    /**
     * Sets up the testing environment.
     */
    @Before
    public void setUpTests() {
        myKey = UUID.randomUUID().toString();
        myBucket = UUID.randomUUID().toString();
        myS3Client.createBucket(myBucket);
    }

    /**
     * Tears down the testing environment.
     */
    @After
    public void tearDownTests() {
        myS3Client.deleteObject(myBucket, myKey);
        myS3Client.deleteBucket(myBucket);
    }

    /**
     * Tests the default constructor.
     *
     * @param aContext A testing context
     */
    @Test
    public void testS3ClientServiceImplVertx(final TestContext aContext) {
        final AwsCredentials awsCreds = new AwsCredentials(myAccessKey, mySecretKey);
        final S3ClientOptions config = new S3ClientOptions(myEndpoint).setCredentials(awsCreds);
        final S3ClientService service = new S3ClientServiceImpl(myContext.vertx(), config);
        final JsonObject testObject = new JsonObject().put(myKey, myBucket);
        final Async asyncTask = aContext.async();

        service.put(myBucket, myKey, new S3ObjectData(testObject.toBuffer()), put -> {
            if (put.succeeded()) {
                try (S3Object s3Obj = myS3Client.getObject(myBucket, myKey)) {
                    aContext.assertEquals(myBucket, s3Obj.getBucketName());
                    aContext.assertEquals(myKey, s3Obj.getKey());
                    s3Obj.getObjectContent().abort();
                    s3Obj.close();
                } catch (final IOException details) {
                    aContext.fail(details);
                } finally {
                    complete(asyncTask);
                }
            } else {
                aContext.fail(put.cause());
            }
        });
    }

    /**
     * Tests putting a resource to S3.
     *
     * @param aContext A testing context
     */
    @Test
    public void testPutJSON(final TestContext aContext) {
        final AwsCredentials credentials = new AwsCredentials(myAccessKey, mySecretKey);
        final S3ClientOptions config = new S3ClientOptions(myEndpoint).setCredentials(credentials);
        final S3ClientService service = new S3ClientServiceImpl(myContext.vertx(), config);
        final JsonObject json = new JsonObject().put(TestConstants.ID, myKey);
        final Async asyncTask = aContext.async();

        service.put(myBucket, myKey, new S3ObjectData(json.toBuffer()), put -> {
            if (put.succeeded()) {
                aContext.assertTrue(myS3Client.doesObjectExist(myBucket, myKey));
                complete(asyncTask);
            } else {
                aContext.fail(put.cause());
            }
        });
    }

    /**
     * Tests getting a resource from S3.
     *
     * @param aContext A testing context
     */
    @Test
    public void testGetJSON(final TestContext aContext) throws SdkClientException, AmazonServiceException {
        final String content = new JsonObject().put(TestConstants.ID, myKey).encodePrettily();
        final AwsCredentials credentials = new AwsCredentials(myAccessKey, mySecretKey);
        final S3ClientOptions config = new S3ClientOptions(myEndpoint).setCredentials(credentials);
        final S3ClientService service = new S3ClientServiceImpl(myContext.vertx(), config);
        final FileSystem fileSystem = myContext.vertx().fileSystem();
        final Async asyncTask = aContext.async();

        // PUT the object that we test into our test S3 bucket
        myS3Client.putObject(myBucket, myKey, content);
        aContext.assertTrue(myS3Client.doesObjectExist(myBucket, myKey));

        // Get the object from the S3 bucket and check its value
        service.get(myBucket, myKey, get -> {
            if (get.succeeded()) {
                get.result().asBuffer(fileSystem).onComplete(asBuffer -> {
                    if (asBuffer.succeeded()) {
                        aContext.assertEquals(myKey, new JsonObject(asBuffer.result()).getString(TestConstants.ID));
                        complete(asyncTask);
                    } else {
                        aContext.fail(asBuffer.cause());
                    }
                });
            } else {
                aContext.fail(get.cause());
            }
        });
    }

}