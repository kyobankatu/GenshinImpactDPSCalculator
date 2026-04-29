import socket
import struct


VERSION = 4

CMD_HELLO = 1
CMD_CREATE_RUNNER = 2
CMD_RESET_RUNNER = 3
CMD_STEP_RUNNER = 4
CMD_CLOSE_RUNNER = 5
CMD_SHUTDOWN = 6


def recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        chunk = sock.recv(size - len(chunks))
        if not chunk:
            raise ConnectionError("Socket closed while receiving data")
        chunks.extend(chunk)
    return bytes(chunks)


def send_int(sock: socket.socket, value: int) -> None:
    sock.sendall(struct.pack(">i", value))


def send_bool(sock: socket.socket, value: bool) -> None:
    sock.sendall(struct.pack(">?", value))


def send_ints(sock: socket.socket, values) -> None:
    sock.sendall(struct.pack(">" + "i" * len(values), *values))


def recv_int(sock: socket.socket) -> int:
    return struct.unpack(">i", recv_exact(sock, 4))[0]


def recv_string(sock: socket.socket) -> str:
    size = recv_int(sock)
    return recv_exact(sock, size).decode("utf-8")


def recv_bool(sock: socket.socket) -> bool:
    return struct.unpack(">?", recv_exact(sock, 1))[0]


def recv_ints(sock: socket.socket, count: int):
    return list(struct.unpack(">" + "i" * count, recv_exact(sock, 4 * count)))


def recv_bools(sock: socket.socket, count: int):
    return list(struct.unpack(">" + "?" * count, recv_exact(sock, count)))


def recv_doubles(sock: socket.socket, count: int):
    return list(struct.unpack(">" + "d" * count, recv_exact(sock, 8 * count)))
