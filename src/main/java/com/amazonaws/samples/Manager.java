package com.amazonaws.samples;


import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.util.Base64;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.io.InputStreamReader;
import java.io.PrintWriter;


public class Manager {
	private static AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new ProfileCredentialsProvider().getCredentials());
	private static String bucketName = credentialsProvider.getCredentials().getAWSAccessKeyId().toLowerCase();
	private static AmazonEC2 ec2;
	private static AmazonS3 s3;
	private static AmazonSQS sqs;

	/*
	private static String mySendQueueUrl, myReceiveQueueUrl;
	private static String myReceiveQueueUrlName = "local_receive_manager_queue";
	private static String mySendQueueUrlName = "local_send_manager_queue";
	 */

	private static String sqsManagerWorkerNewTask = "sqsManagerWorkerNewTask";
	private static String sqsWorkerManagerDoneTask = "sqsWorkerManagerDoneTask";
	private static String sqsLocalManagerFileUpload = "sqsLocalManagerFileUpload";
	private static String sqsManagerLocalFileDone = "sqsManagerLocalFileDone";
	private static String myLocalSendQueueUrl, myReceiveQueueUrl;
	private static String myJobWorkerQueueUrl;
	private static String myDoneWorkerQueueUrl;
	private static String[] processedUrl;
	private static String[] UrlList;
	private static int NumberOfMessagesPerWorker = 0;
	private static List<Instance> workersList = new ArrayList<Instance>();
	private static int NumberOfactiveWorkers = 0;
	private static int NumOfUrlsToProcess = 0;
	public static IamInstanceProfileSpecification instanceP;
	public static boolean proceedThread2 = false;


	public static void main(String[] args) throws IOException {
		BuildTools();

		//Thread 1
		Thread LocalManagerMessageReceiveThread = new Thread(() -> {
			try {
				localMessageListener();
			} catch (IOException e) {
				System.out.println("Couldnt run Thread 1");
				e.printStackTrace();
			}
		});
		LocalManagerMessageReceiveThread.start();

		//Thread 2
		Thread ManagerWorkerMessageReceiveThread = new Thread(() -> {
			workerMessageListener();
		});
		ManagerWorkerMessageReceiveThread.start();
	}

	private static void workerMessageListener() {
		System.out.println("num of urls to process is: " + NumOfUrlsToProcess + "start while loop");
		System.out.println("PROCEEDTHREAD2 IN THREAD 2: " +proceedThread2 + " start while loop");

		while(!proceedThread2) {
			
		}
		System.out.println("num of urls to process is: " + NumOfUrlsToProcess + "end of while loop");
		System.out.println("PROCEEDTHREAD2 IN THREAD 2: " +proceedThread2 + " end of while loop");


		getMessagesFromDoneTaskQ();
		File file =createHTML();
		uploadSummaryFileToS3(file);
		sendMessageToFileDoneQ();
		System.out.println("@@@@@MANAGER HAS DONE ALL TASKS@@@@@");

	}
	private static void uploadSummaryFileToS3(File HTMLsummaryFile) {
		System.out.println("UPLOAD SUMMARY FILE TO S3");
		PutObjectRequest req = new PutObjectRequest(bucketName, "output.html", HTMLsummaryFile);
		s3.putObject(req);
		System.out.println("UPLOAD SUMMARY FILE TO S3 COMPLETED");

	}

	private static File createHTML() {
		System.out.println("@@@@@@@@@@CREATE HTML@@@@@@@@@@@@@@@@@@@@");

		try {
			File file = new File ("output.html");
			PrintWriter writer = new PrintWriter(file);
			writer.write("<!DOCTYPE html>\n" + "<html>\n" + "<body>\n");
			System.out.println("URLLIST.LENGTH = " +UrlList.length );

			for(int i = 0; i< UrlList.length;i++) {
				System.out.println("INSIDE CREATE HTML LOOP, i = " + i);

				writer.write("<p><img src=" + processedUrl[i] + "><br>");
				writer.write("\n<p>");
				writer.write(UrlList[i]);
			}
			writer.write("</body>\n" + "</html>");
			writer.close();
			System.out.println("Creation of HTML file was completed");
			System.out.println("file.toString : " + file.toString());
			return file;


		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Error on creating HTML file");

		return null;

	}

	private static void sendMessageToFileDoneQ() {
		System.out.println("SENDING MESSAGE TO DONE Q");

		myReceiveQueueUrl = createAndGetQueue(sqsManagerLocalFileDone);
		String doneTask = "done task@@@" + bucketName;
		System.out.println("done tasK");
		sqs.sendMessage(new SendMessageRequest(sqsManagerLocalFileDone, doneTask));
		System.out.println("THE DONE MESSAGE HAS BEEN SENT PROPERLY");

	}

	private static void getMessagesFromDoneTaskQ() {
		myDoneWorkerQueueUrl = createAndGetQueue(sqsWorkerManagerDoneTask);

		processedUrl = new String[NumOfUrlsToProcess];
		UrlList = new String[NumOfUrlsToProcess];
		int numOfDoneUrls = 0;
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(myDoneWorkerQueueUrl);

		while(numOfDoneUrls < NumOfUrlsToProcess) {
			System.out.println("num of done urls  : " + numOfDoneUrls);
			//System.out.println(sqs.receiveMessage(receiveMessageRequest).getMessages().toString());
			for(Message message : sqs.receiveMessage(receiveMessageRequest).getMessages()) {
				System.out.println("message (WorkerMessageListener) : " + message);
				//gets the done url
				processedUrl[numOfDoneUrls]= message.getBody().split("@@@")[1];
				UrlList[numOfDoneUrls]= message.getBody().split("@@@")[2];

				String messageRecieptHandle = message.getReceiptHandle();
				sqs.deleteMessage(new DeleteMessageRequest(myDoneWorkerQueueUrl, messageRecieptHandle));
				numOfDoneUrls++;
			}
		}
		for(int i = 0; i<processedUrl.length;i++) {
			System.out.println("processedUrl["+i+"]: " +processedUrl[i]);
		}
		for(int i = 0; i<UrlList.length;i++) {
			System.out.println("UrlList["+i+"]: " +UrlList[i]);
		}
		//closeInstances();


	}

	private static void localMessageListener() throws IOException{
		S3Object object = getFile(s3);
		//creates Manager->Worker queue in order to send new tasks to worker
		myJobWorkerQueueUrl = createAndGetQueue(sqsManagerWorkerNewTask);
		int numOfUrls = sendUrlsToMessageQueue(object);
		NumOfUrlsToProcess = numOfUrls; 
		System.out.println("number of urls : " + numOfUrls);
		System.out.println("NumOfUrlsToProcess : " + NumOfUrlsToProcess);
		proceedThread2 = true;
		System.out.println("proceedThread2: " + proceedThread2);


		startWorkers(numOfUrls);
	}

	private static void startWorkers(int numOfUrls) {
		System.out.println("startWorkers method");
		int workersNeeded = numOfUrls / NumberOfMessagesPerWorker;// we get NumberOfMessagesPerWorker(n) from the message queue
		if(NumberOfactiveWorkers < workersNeeded) {
			for(int i = 0; i < workersNeeded - NumberOfactiveWorkers; i++)
				createWorkesrInstance(bucketName);//creates the missing workers
			NumberOfactiveWorkers = workersNeeded;
		}
	}


	//sends new tasks(urls) to message queue and returns number of urls to be done.
	private static int sendUrlsToMessageQueue(S3Object object) throws IOException {
		System.out.println("\n object  : " +object);
		int counter = 0;
		//InputStream objectData = object.getObjectContent();
		BufferedReader reader = new BufferedReader(new InputStreamReader(object.getObjectContent()));
		String line;

		while((line = reader.readLine()) != null) {
			System.out.println("----------------------------------------------counter: " + counter + "line: " + line+"----------------------------------------------");
			// send new worker job to sqsManagerWorkerNewTask queue
			sqs.sendMessage(new SendMessageRequest(sqsManagerWorkerNewTask, "new image task@@@" + bucketName + "@@@" + line));
			counter++;


		}
		return counter;
	}

	private static S3Object getFile(AmazonS3 s3) {
		System.out.println("getfile \n");
		String[] parseMessage = null;
		//gets the file from local queue
		myReceiveQueueUrl = createAndGetQueue(sqsLocalManagerFileUpload);

		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(myReceiveQueueUrl);
		//System.out.println(sqs.receiveMessage(receiveMessageRequest).getMessages().toString());
		for(Message message : sqs.receiveMessage(receiveMessageRequest).getMessages()) {
			System.out.println("message : " + message);
			if(message == null)
				continue;
			else {
				parseMessage = message.getBody().split("@@@");
				NumberOfMessagesPerWorker = Integer.parseInt(parseMessage[3]);
				S3Object object;
				try {
					System.out.println(" before getobject ");
					System.out.println(message.getBody().substring(message.getBody().lastIndexOf('/') + 1));
					object = s3.getObject(new GetObjectRequest(bucketName, "input.txt"));// TOFIX INPUT.TXT
					return object;

				}
				catch (Exception e) {
					continue;
				}
			}
		}
		return null;
	}

	private static void BuildTools() {
		ec2 = AmazonEC2ClientBuilder.standard()
				.withCredentials(credentialsProvider)
				.withRegion("us-east-1")
				.build();

		sqs = AmazonSQSClientBuilder.standard()
				.withCredentials(credentialsProvider)
				.withRegion("us-east-1")
				.build();

		s3 = AmazonS3ClientBuilder.standard()
				.withCredentials(credentialsProvider)
				.withRegion("us-east-1")
				.build();

		instanceP = new IamInstanceProfileSpecification();
		instanceP.setArn("arn:aws:iam::692054548727:instance-profile/EgorNadavRole");

	}

	private static String createAndGetQueue(String queueName) {
		for (String queueUrl : sqs.listQueues().getQueueUrls()) {
			if(queueName.equals(queueUrl.substring(queueUrl.lastIndexOf('/') + 1)))
				return queueUrl;
		}

		try {
			CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName);
			return sqs.createQueue(createQueueRequest).getQueueUrl();
		}

		catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which means your request made it " +
					"to Amazon SQS, but was rejected with an error response for some reason.");
		} 

		catch (AmazonClientException ace) {
			System.out.println("Error Message: " + ace.getMessage());
		}

		return null;
	}

	private static List<Instance> createWorkesrInstance(String bucketname) {
		RunInstancesRequest request = new RunInstancesRequest("ami-0ff8a91507f77f867", 1, 1);
		request.setInstanceType(InstanceType.T2Micro.toString());
		ArrayList<String> commands = new ArrayList<String>();
		commands.add("#!/bin/bash\n"); //start the bash
		commands.add("sudo su\n");
		commands.add("echo @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n");
		commands.add("echo @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n");
		commands.add("echo @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n");
		commands.add("echo @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n");
		commands.add("yum -y install java-1.8.0 \n");
		commands.add("alternatives --remove java /usr/lib/jvm/jre-1.7.0-openjdk.x86_64/bin/java\n");
		commands.add("aws configure set aws_access_key_id " + new ProfileCredentialsProvider().getCredentials().getAWSAccessKeyId());
		commands.add("aws configure set aws_secret_access_key " + new ProfileCredentialsProvider().getCredentials().getAWSSecretKey());
		commands.add("# Bootstrap: download jar from S3 and run it");
		commands.add("wget https://"+ bucketName + ".s3.amazonaws.com/" + "Worker.jar" +" -O ./" + "Worker.jar" );
		commands.add("java -jar Worker.jar");

		StringBuilder builder = new StringBuilder();

		Iterator<String> commandsIterator = commands.iterator();

		while (commandsIterator.hasNext()) {
			builder.append(commandsIterator.next());
			if (!commandsIterator.hasNext()) {
				break;
			}
			builder.append("\n");
		}
		request.setIamInstanceProfile(instanceP);
		String userData = new String(Base64.encode(builder.toString().getBytes()));
		request.setUserData(userData);
		List<Instance> instances = ec2.runInstances(request).getReservation().getInstances();
		for(Instance instance : instances) {
			/*CreateTagsRequest requestTag = new CreateTagsRequest();
			requestTag = requestTag.withResources(instance.getInstanceId())
					.withTags(new Tag("Worker", ""));
			ec2.createTags(requestTag);
			 */
			//workersList.add(instance);

		}
		workersList.addAll(instances);
		System.out.println("Launch instance: " + instances);

		return instances;
	}
	/*private static void closeInstances() {
        List<String> toCloseList = new ArrayList<>();
        if (workersList != null) {
            for (Instance i : workersList)
                toCloseList.add(i.getInstanceId());
            TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest(toCloseList);
            ec2.terminateInstances(terminateRequest);
        }
    }*/
}