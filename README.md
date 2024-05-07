# AliceDL
Alice in DistributedLand - Advanced Programming of Cryptographic Methods projects

Enrico Carnelos - Annachiara Fortuna - Marcel Swidersky
20 December 2023

## Description

AliceDL is a practical system designed to store secret files securely using a network of distributed servers. The application is available through command line interface. Users can upload and download files from the system easily.

The application responds to the need of storing important files securely using a distributed system. Saving the entire file in a unique server means having a unique point of failure: in case the server does not respond due to internal error or for a DDos attack, the file is not available. Similarly, if the file is entirely duplicated in many servers, availability is guaranteed, but confidentiality can be compromised due to the increase of the probability of having a vulnerable server. Instead, fragmentation takes the best of both approaches, an attacker would have to gain control over a multitude of servers and crack the encryption of all chunks in order to retrieve the original file.

To upload a file, a passphrase of at least 12 and at most 64 characters should be provided. To download a file, the same passphrase is needed along with a token in order to retrieve the file from the system. The token is given when the file is uploaded successfully.

The servers in the network are in a peer-to-peer relation and they are managed by a distributed hash table algorithm (DHT). The main features of a DHT is that when the servers join the network a hash digest is assigned to them based on some server unique characteristics such as the ip address. The files are stored based on the digest of each chunk, indeed the client computes the hash digest and selects the server with the nearest id. Following the hash properties, the look-up of the servers is very efficient and the ids are unique and homogeneously spread. For the purpose of the project the network of servers is fixed, which means that new servers can’t join or leave the network.

The entire file is divided in chunks and for each of them the hash of the next chunk is appended. Then the chunk with the appended hash is encrypted with a symmetric key encryption. For each chunk a different derived key is used. The keys are obtained from a key derivation function that takes as input the chosen passphrase inserted by the user.

In this way, the user can decrypt the file chunk by chunk, always knowing from which server to retrieve the next chunk. As the hash acts as an ‘accessor’ to retrieve the chunk it corresponds to, the location where the next chunk is stored remains hidden until the previous chunk is decrypted. Thus, a malicious user cannot even retrieve the full ciphertext using a brute-force attack.

Moreover, the same chunk is saved in k different servers, as replication is needed in case of failure of a server to guarantee availability. K is set to 15% of the number of servers in the network and should be at least 2. When a server receives a chunk from the client, it sends to the others k-1 nearest servers a replica message with the same chunk.

## Functional Requirements

The system provides a way to upload and download files in a network of servers to many users. The same file can be downloaded by a user different from the uploader, provided the token and the passphrase. The token is a string printed when the file is successfully uploaded and works as an identifier for the entire file. 

For the purpose of the project the network of servers is fixed, meaning that no one can enter or exit. The project provides a way to start a desired number of servers to test the architecture. 

The system automatically handles the division of the file, the generation of the keys, the computation of the hash and the encryption of the chunks, the composition of the messages and its replication, as well as the decryption and recomposition of the original message after a successful download request. 

The interaction with the system is handled via command line.

## Security Requirements

- *Availability* is guaranteed by the replication of the chunks in different servers. Thus, if a server fails, the chunk is available on other servers. The redundancy parameter k is set to 15% of the total number of servers in the system, rounded up to the nearest integer. For availability purposes, k can’t be lower than 2. Considering a system where the servers can enter and exit from the network, if a chunk is not found on a server where it is supposed to be, the others handle the replica of the file on another server, to maintain the constraint of having k copies. Unfortunately, this is not possible in a fixed network of servers.
- *Confidentiality* is provided, since chunks are not stored in cleartext, but encrypted with a symmetric key algorithm before storage. After retrieval of a chunk, neither the plain data nor the location of the following frame is known before successful decryption. Moreover, the key used to encrypt each chunk is different, so, also being able to brute-force a key will not reveal the content of the entire file. Furthermore, the primitive used to hash the file is keyed, so that also the hash doesn’t provide information and is authenticated. The keys are provided by the same key derivation mechanism used for encryption, which uses a master key to generate the other. 
- *Integrity* of the entire file is ensured through the integrity of each chunk. Along with the encrypted chunk and the position of the following chunk, the hash of the following frame is stored. During the recomposition process the hash of all chunks is checked. If the provided digest and the computed one are different, the chunk is corrupted or has been wrongly decrypted, so the process stops and another request should be made. Moreover the file can’t be reconstructed putting chunks in a different order and without decrypting everything step by step. To increase the level of security, a primitive which performs authenticated encryption has been used; it computes an authentication tag to check the message has not been tampered. 

## Technical Details

### Architecture

The main building blocks of the projects are the key derivation, chunk processing (splitting, encryption, packing, unpacking, decryption, rebuilding) and the DHT network.

### DHT Network

The aim of this part of the project is to set up the secure connection between servers and the client. The entire network is based on a peer-to-peer relationship managed by the Distributed Hash Table (DHT) algorithm, which allows autonomous behaviour.

When the system starts, the servers join the network by storing their information in the nodesList.json file. The file is cleared and rebuilt every time the servers are started. As the project implements a fixed size network, client and servers rely on the nodesList.json file that maps the relation between hostname and hash digest. Instead, in a real environment a bootstrap server is needed to handle the json file. 

The client-server and server-server communications use the TLS1.3 protocol with the “TLS_AES_256_GCM_SHA384” cipher suite, which is the most secure one.

Once a client wants to send a chunk to a server, it computes the digest of the chunk and based on the nodesList.json file retrieves the k nearest servers, where k is the redundancy parameter, i.e. the number of servers that should store the same chunk. This parameter is set to 15% of the number of active servers. The nearest servers are retrieved based on the hash of the chunk and the hash identifier of the servers themself. 

To upload a chunk, the client sends a PUT message to the nearest server. When the server gets the request, it stores the chunk locally and answers with an OK message. After answering, it sends a replica message to the k-1 nearest servers to set the k replicas in the network .

If the server does not answer or answers with an ERROR message, the client tries with the others nearest servers, one by one. If for all the k nearest servers the client does not receive a successful answer, the uploading phase terminates with an error.

When a client wants to retrieve a chunk from a server, it sends a GET message to the nearest server. The server searches locally for the chunk and sends it to the client. If the server does not have the chunk or does not answer, the client sends the same request to the other k-1 nearest servers, one by one. If none answers successfully, the downloading phase is terminated with an error message.

### Key derivation

The key derivation part handles the generation of the subkeys from the passphrase given by the user when the file is uploaded. To be more specific, the subkeys are not derived by the passphrase directly, but by the master key, which is derived by the passphrase, with a generic hash function. 

The subkeys are needed for the encryption and decryption of the chunk of the initial file and to compute the authenticated hash. The number of subkeys retrieved is not fixed. It is specified based on the length of the file considered. Since it is not possible to derive infinite subkeys from the same master key, the maximum amount is fixed in order to keep the derivation secure. The control has been implemented, even if the amount of possible subkeys is not likely to be reached. The limit is 2^64 subkeys from the same masterkey and context. To increase the level of unpredictability, context string and salt are randomly generated. The context is used to bind the information to a specific context, so that the same data can’t be reused in other situations. The salt is a random value that is used for hashing to avoid rainbow table attacks and is used to derive the master key from the passphrase. Since context and salt should not be secret, for the explained reasons, they are saved in clear in each chunk. The meaning of saving this information is that the same keys should be used for encryption and decryption and to obtain them, the same parameters should be specified.

### FIle processing

This part of the architecture focuses on preparing the file for upload and download from the system. The file is split into at least 3 chunks of at most 10MB. Then, the chunk data is concatenated with the next hash in the chain and finally encrypted:
$$
out_n=Enc(in_n||Hash(in_n+1))
$$
The end of the hash chain is marked by a constant *End of File* hash.

This algorithm ensures that successful decryption of one chunk is necessary to determine which file from which server to download next. Authenticated Encryption ensures integrity protection. Usage of a keyed hash function ensures not only that the output is random even for equal input data, but also that no information is leaked, as the hashes are stored in plain as the file name of their respective chunk on the server.

## Implementation

The implementation of the project has been performed in Java. It was chosen because it provides many native libraries for the connection of servers and good class handling strategies. Specific libraries have been used for the different components. The complete list of libraries used is visible in the project directory /lib. In the following part, the most important are described.

**Libsodium** with the Java binding **LazySodium** has been used for cryptographic primitives. Among them:

- *cryptoGenericHash*, a primitive to generate the masterKey from the passphrase and the digest of the chunk. It provides keyed hashing. This method is preferred over the pwHash function because it produces a deterministic output.
- *cryptoKdfDeriveFromKey*, a primitive to compute the subkeys from the masterkey, specifying id and context. This function has been used to implement two different methods, used to retrieve subkeys. One of them for encryption, when the number of total subkeys needed is known, and one of them for decryption. The second one retrieves the subkeys specifying the desired subkey id, so that they can be generated one by one;
- *cryptoSecretBoxEasy* and *cryptoSecretBoxOpenEasy*, function for authenticated encryption and decryption.

**SSLSocket** has been used for the authenticated and encrypted server connection and network. 

Moreover, **commons-cli** has been used to help with the command line interface starting. 

## Code Structure

The project has two main folders, the **data** folder where that contains all the files used to menaged the system and the **src** folder containing the source code:

```
├── data
│  ├── DB
│  ├── keys
│  │  ├── keystore
│  │  └── truststore
│  └── nodesList.json
├── playground
└── src
  ├── AliceDL.java
  ├── crypto
  │  ├── FileProcessor.java
  │  ├── KeyDerivation.java
  │  └── Sodium.java
  ├── middleware
  │  ├── FilesManager.java
  │  ├── MessageHandler.java
  │  ├── NodesConnectionHandler.java
  │  └── NodesListHandler.java
  └── nodes
    ├── Client.java
    └── Node.java
```

### Data 

The data folder contains a **DB** folder where each server creates their own folder to store the chunks file simulating a local database. 

The **keys** folder stores information related to the certificates used to authenticate the servers for the TLS communication, in particular for the project self-signed certificates were created. 

The **nodeList.json** is a file in json format storing all the information about the servers, such as port, host and files contained in each server. At the end of this file there is also the redundancy parameter, which is computed as the 15% of the number of active servers, with a minimum of 2. 

### Playground

Folder containing sample file for testing purpose

### Src

**AliceDL.java** is the main class. It handles the parsing of commands, checking for the parameter values in input. Then the proper functions from the other files are called in order to process the request. 

**Crypto** is a package containing files with functions that handles the cryptographic aspects of the project:

- **FileProcessor.java**, this file contains the functions to process the file, both in upload and download. The function to upload the file, takes the file and the passphrase as input. It divides the file into chunks, computes the hash of them, composes the messages and after encrypting, sends them to the servers. It also prints the token needed to download the file. The function to download the file takes the token, the passphrase and the destination where to save the file as input. It derives the subkeys from the passphrase, requires the file from the servers and recomposes the initial file. The recomposition process is iterative and involves retrieving the chunk from the server, decrypting it, computing the hash and checking it. Both functions return true if the process is completed successfully and false otherwise. 
- **KeyDerivation.java**, fIle containing the functions to derive the masterkey from the passphrase and the subkeys from the master key. There are different versions of the functions to use for decryption and encryption. For encryption, parameters such as the salt and the context are generated randomly, while in decryption they have to be specified properly in order to obtain the same subkeys.
- **Sodium.java**, this file performs the configuration of Libsodium library, which is done using LazySodium as Java Binding, since the original library is written in C. A singleton pattern is used for easy access of the sodium object.

**Middleware** is a package containing files with functions used to manage the messages from client and server, the connections and the list of the servers, in particular contain some static method with the “synchronised” :

- **FilesManager.java**, this static class is used by the server to read and save chunk files. The methods require chunk id to complete all the operations.
- **MessageHandler.java**, this class contains all the methods that create the packet for the communication and handle client communication implementing the algorithm explained in the DHT section.
- **NodesConnectionHandler.java**, this is a class that extends the thread method. This thread is called by the server when it accepts a client connection. This class receives and reads the request from the client and handles it.
- **NodesListHandler.java**, this is a static class that handles the common json file that maps the relation between ids and hostnames. Manage the join and leave of a node and modify the redundancy params based on the network size. In particular, there is a method that returns a list with the nearest servers based on the chunk id.

**Nodes** is a package that handles the socket connection between client and server in the TLS protocol:

- **Client.java**, this class creates the socket tls client communication and has a method to send data to the server.
- **Node.java**, this class creates the socket tls server communication. In case of a client request, launch the NodesConnectionHandler thread. In particular, this class is a thread but only for simulation purposes, in this way a single main file can launch multiple server instances.

## Security Considerations

The purpose of this project was to implement a secure system where you can upload files in a secure way. In addition, our two-level redundancy approach with a distributed network of servers and replication of chunks to neighbouring servers ensures the absence of a single point of failure. To guarantee Integrity, Confidentiality and Availability, we consider the following security aspects:

### DHT network

- The servers in the network are connected and authenticated using a TLS certificate. This is needed because the communication between servers and clients is protected by the TLS protocol, which ensures integrity and confidentiality.
- In particular the tls1.3 protocol is used with the “TLS_AES_256_GCM_SHA384” cipher that guarantees the maximum current security level and due to the use of the diffie hellman key exchange also forward secrecy is guaranteed.
- For the management of the network of servers, a Distributed Hash Table algorithm has been used. Moreover, each chunk is stored in the server whose hash identifier is nearest to the hash of the chunk itself. This method enables an equal distribution of files, without overloading one server over the others. The overload of one server is dangerous because if lost or attacked, it will result in the loss of more information. The extreme consequence of this is having a single point of failure.
- Our system is robust against possible exceptions and edge cases and always behaves deterministically. In addition, all the communication errors between client and server are handled by transmitting the same error message, thus any type of oracle attack is avoided. For debug and log purposes, the server writes a different type of message on the stout, but these messages are not transmitted to the client.

### File processing

- Since deriving infinite subkeys from the same MasterKey is not secure, the limit for the used primitive from Libsodium has been checked. It is up to 264 which is a huge number, with respect to the needs of the project. Indeed, since the dimension of each chunk is set to a maximum of 10 Mb limit number of keys is reachable only with a file 264 *10Mb, which is highly improbable to upload in this type of project.
- Libsodium provides all cryptographic primitives for this project. The easy-to-use nature of this library and its limited functionality reduce the possibility of introducing cryptographic malpractice in the code.
- For the chunk encryption the authenticated encryption function of libsodium is used. The function with a key and a nonce given as input provides both authentication and confidentiality by using the XSalsa20 and Poly1305 primitives. The nonce does not need to be confidential and it is appended on the chunk in plain text to allow for decryption. The nonce is randomly generated for every chunk in order to use the same nonce only one time.
- Libsodium’s suggested constants are used whenever a length or size has to be determined (e.g. key length). This ensures that if the suggested constants change in the future, the up-to-date value is used. In addition, no keys are hardcoded, but instead are generated in the proper context.
- Libsodium’s secure random generator is used for the generation of context, nonce and salt.
- Checks for limits of algorithms and size consented have been made, as well as the set up of methods to protect the system from improper use. Using primitives without taking care of this highly increases the vulnerability level.
- The key derivation primitive, used to retrieve subkeys from the master key and provided by libsodium, is implemented using Blake2B algorithm. It requires to specify a context, which is a string used to bind the information to that specific situation and avoid the same subkey to be reused in different context improperly. The context we use is randomly generated and different for each file, avoiding the type of attacks mentioned above.

## Known Limitations

- In this version of the project the *removal of a file* from the system is not possible. It would be necessary to set up a method to recognize who tries to perform this operation in order to understand if that user can do that or not, possibly with user certificates or a private key. Otherwise, if the hash of the chunk is leaked, anyone can delete it
- If the *number of chunks is bigger compared to the number of servers available* in the network or if the redundancy is too high, the amount of chunks on each server is high and each server contains all the files. As long as the number of servers is large enough, this issue does not occur. It would be possible to perform some sort of controls to avoid this behaviour by selecting the amount of chunks based on the amount of servers.
- In this version of the project, the *network of servers is static*, so nobody can join or leave the network. This is due to the demonstrative nature of this project and does not impair functionality, however, in a real-world environment, this would need to be changed.
- For the purpose of the project, the servers are simulated by tls sockets all connected to the same IP. Only one certificate is used for the TLS authentication of all the servers. In a real environment each server will use its own certificate and will be running on a separate machine to improve availability.
- The application is slower when files of big size are uploaded. Even if fast symmetric encryption is used, the transmission of the files could be speeded up and optimised to have better performance. 

## Instructions for Installation and Execution

The program can be used through a CLI. There is only an executable file AliceDL.jar, that can be run followed by different parameters and allows to start both the client and the server. 

- Firstly, it is important to activate the network of servers. The command line interface requests to specify the number of servers and the port to use as a starting point for the creation of the tcp sockets. The order of the parameters can be changed. The complete command is:

**java -jar AliceDL.jar -server <number of server to start> -p <starting port number>**

- To **upload** a file in the system the same executable should be run, but the parameters should change. The path of the and a passphrase should be specified. The command will be:

**java -jar AliceDL.jar -put <filePath> -pass <passphrase>**

- In order to **download** a file, the token given to retrieve the file must be specified, along with the passphrase and the location where to save it.

**java -jar AliceDL.jar -get <token> -pass <passphrase> -d <destinationPath>**

If the AliceDL.jar file is moved from its original position, the system will not work. 

The application requires [JDK 19.0](https://www.oracle.com/java/technologies/javase/jdk19-archive-downloads.html) to be installed on the running machine. 

Important note, we have tested the application only in a UNIX operating system.
