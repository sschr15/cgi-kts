#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#ifndef SOCKET_LOCATION
#error "SOCKET_LOCATION must be defined"
#define SOCKET_PATH ""
#else
#define S(x) #x
#define S_(x) S(x)
#define SOCKET_PATH S_(SOCKET_LOCATION)
#endif

#ifdef LOG
#define DEBUG(...) fprintf(stderr, __VA_ARGS__)
#else
#define DEBUG(...)
#endif

#define RESPONSE_ERROR "Http/1.1 500 Internal Server Error\r\n" \
                       "Content-Type: text/plain\r\n" \
                       "Content-Length: 58\r\n" \
                       "\r\n" \
                       "There was an error communicating with the script server.\r\n"

int server_fd;

void signal_handler(int signum) {
    DEBUG("Received signal %d\n", signum);
    if (signum == SIGUSR1) {
        // char general_buffer[1048576];
        // int bytes_read;

        // do {
        //     bytes_read = read(server_fd, general_buffer, 1048576);
        //     if (bytes_read == -1) {
        //         perror("read");
        //         fprintf(stderr, "No bytes read after SIGUSR1\n");
        //         printf(RESPONSE_ERROR);
        //         close(server_fd);
        //         exit(1);
        //     }
        //     printf("%.*s", bytes_read, general_buffer);
        // } while (bytes_read == 1048576);

        int length;
        if (read(server_fd, &length, sizeof(int)) != sizeof(int)) {
            perror("read length");
            printf(RESPONSE_ERROR);
            close(server_fd);
            exit(1);
        }
        if (length < 0) {
            fprintf(stderr, "Received -1 length after SIGUSR1\n");
            printf(RESPONSE_ERROR);
            close(server_fd);
            exit(1);
        }
        char *buffer = malloc(length + 1);
        if (read(server_fd, buffer, length) != length) {
            perror("read buffer");
            printf(RESPONSE_ERROR);
            free(buffer);
            close(server_fd);
            exit(1);
        }
        buffer[length] = '\0'; // null-terminate the string
        DEBUG("Received response: %s\n", buffer);
        printf("%.*s", length, buffer);
        free(buffer);

        close(server_fd);

        exit(0);
    }

    fprintf(stderr, "Error: received unexpected signal %d\n", signum);
    close(server_fd);
    exit(1);
}

// Hash function for strings using the same algorithm as Java's String.hashCode()
int hash(const char *str) {
    int hash = 0;
    for (int i = 0; str[i] != '\0'; i++) {
        hash = 31 * hash + str[i];
    }
    return hash;
}

char *base36(int num) {
    char *result = malloc(7);
    for (int i = 5; i >= 0; i--) {
        result[i] = "0123456789abcdefghijklmnopqrstuvwxyz"[num % 36];
        num /= 36;
    }
    result[6] = '\0';
    return result;
}

char *generate_short_name(const char *filename) {
    char *new_name = malloc(17);
    if (strlen(filename) <= 16) {;
        memset(new_name, 0, 17); // ensure null-termination and padding
        strcpy(new_name, filename);
        return new_name;
    }
    strncpy(new_name, filename, 9);
    new_name[9] = 1; // delimiter to disambiguate generated names from original names
    int hash_val = hash(filename);
    char *hash_str = base36(hash_val);
    strncpy(new_name + 10, hash_str, 6);
    free(hash_str);
    new_name[16] = '\0';
    return new_name;
}

/*
 * Execute a Kotlin main script found in (executable-directory)/../scripts/
 * (used in order to act as a cgi-bin script)
 */
int main(int argc, char **argv) {
#ifndef RUN_AS_MAIN
    char *dir = strdup(argv[0]); // .../public_html/cgi-bin/filename

    char *filename = strrchr(dir, '/');
    if (filename == NULL) {
        fprintf(stderr, "Error: could not find last slash in %s\n", dir);
        printf(RESPONSE_ERROR);
        return 1;
    }

    filename++;
#else
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <filename>\n", argv[0]);
        return 1;
    }

    char *filename = argv[1];
#endif

    struct sigaction sa;
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGUSR1, &sa, NULL);

    server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd == -1) {
        perror("socket");
        printf(RESPONSE_ERROR);
        return 1;
    }

    struct sockaddr_un addr;
    addr.sun_family = AF_UNIX;
    strcpy(addr.sun_path, SOCKET_PATH);

    if (connect(server_fd, (struct sockaddr *)&addr, sizeof(addr)) == -1) {
        perror("connect");
        printf(RESPONSE_ERROR);
        return 1;
    }

    char *short_name = generate_short_name(filename);
    DEBUG("Sending short name %s\n", short_name);
    write(server_fd, short_name, 17);

    free(short_name);

#ifndef RUN_AS_MAIN
    free(dir);
#endif

    char general_buffer[1048576];
    char *buffer = general_buffer;
    char next_byte;
    int bytes_read = 0;

    while (1) {
        bytes_read = read(server_fd, &next_byte, 1);
        if (bytes_read == -1) {
            perror("read");
            printf(RESPONSE_ERROR);
            return 1;
        }

        if (bytes_read == 0) {
            fprintf(stderr, "No bytes read\n");
            printf(RESPONSE_ERROR);
            return 1;
        }

        if (next_byte) {
            DEBUG("%d\n", next_byte);
            raise(SIGUSR1);
        }

        do {
            bytes_read = read(server_fd, &next_byte, 1);
            if (bytes_read == -1) {
                perror("read");
                printf(RESPONSE_ERROR);
                return 1;
            }
            if (bytes_read == 0) {
                continue;
            }
            *buffer = next_byte;
            buffer++;
        } while (next_byte != 0);

        DEBUG("Received environment variable request: %s\n", general_buffer);

        char *variable = getenv(general_buffer);
        if (variable != NULL) {
            DEBUG("Sending response: %s\n", variable);
            int length = strlen(variable);
            write(server_fd, &length, sizeof(int));
            write(server_fd, variable, length);
        } else {
            // a zero-length environment variable is unlikely but possible, so use -1 as the not-found indicator
            DEBUG("No such environment variable\n");
            int minus_one = -1;
            write(server_fd, &minus_one, sizeof(int));
        }

        buffer = general_buffer;
    }
}
