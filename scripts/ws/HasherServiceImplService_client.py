##################################################
# file: HasherServiceImplService_client.py
# 
# client stubs generated by "ZSI.generate.wsdl2python.WriteServiceModule"
#     /usr/bin/wsdl2py HasherService
# 
##################################################

from HasherServiceImplService_types import *
import urlparse, types
from ZSI.TCcompound import ComplexType, Struct
from ZSI import client
from ZSI.schema import GED, GTD
import ZSI

# Locator
class HasherServiceImplServiceLocator:
    HasherServiceImplPort_address = "http://localhost:8081/ws/HasherService"
    def getHasherServiceImplPortAddress(self):
        return HasherServiceImplServiceLocator.HasherServiceImplPort_address
    def getHasherServiceImplPort(self, url=None, **kw):
        return HasherServiceImplServiceSoapBindingSOAP(url or HasherServiceImplServiceLocator.HasherServiceImplPort_address, **kw)

# Methods
class HasherServiceImplServiceSoapBindingSOAP:
    def __init__(self, url, **kw):
        kw.setdefault("readerclass", None)
        kw.setdefault("writerclass", None)
        # no resource properties
        self.binding = client.Binding(url=url, **kw)
        # no ws-addressing

    # op: hashAsynchronously
    def hashAsynchronously(self, request, **kw):
        if isinstance(request, hashAsynchronously) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(hashAsynchronouslyResponse.typecode)
        return response

    # op: hash
    def hash(self, request, **kw):
        if isinstance(request, hash) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(hashResponse.typecode)
        return response

    # op: getAsynchronousHashResult
    def getAsynchronousHashResult(self, request, **kw):
        if isinstance(request, getAsynchronousHashResult) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(getAsynchronousHashResultResponse.typecode)
        return response

    # op: getAllAsynchronousHashResults
    def getAllAsynchronousHashResults(self, request, **kw):
        if isinstance(request, getAllAsynchronousHashResults) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(getAllAsynchronousHashResultsResponse.typecode)
        return response

    # op: removeAsynchronousHashRequest
    def removeAsynchronousHashRequest(self, request, **kw):
        if isinstance(request, removeAsynchronousHashRequest) is False:
            raise TypeError, "%s incorrect request type" % (request.__class__)
        # no input wsaction
        self.binding.Send(None, None, request, soapaction="", **kw)
        # no output wsaction
        response = self.binding.Receive(removeAsynchronousHashRequestResponse.typecode)
        return response

hashAsynchronously = GED("http://hasher.ws.lockss.org/", "hashAsynchronously").pyclass

hashAsynchronouslyResponse = GED("http://hasher.ws.lockss.org/", "hashAsynchronouslyResponse").pyclass

hash = GED("http://hasher.ws.lockss.org/", "hash").pyclass

hasherParams = GTD("http://hasher.ws.lockss.org/","hasherWsParams",lazy=False)

hashResponse = GED("http://hasher.ws.lockss.org/", "hashResponse").pyclass

getAsynchronousHashResult = GED("http://hasher.ws.lockss.org/", "getAsynchronousHashResult").pyclass

getAsynchronousHashResultResponse = GED("http://hasher.ws.lockss.org/", "getAsynchronousHashResultResponse").pyclass

getAllAsynchronousHashResults = GED("http://hasher.ws.lockss.org/", "getAllAsynchronousHashResults").pyclass

getAllAsynchronousHashResultsResponse = GED("http://hasher.ws.lockss.org/", "getAllAsynchronousHashResultsResponse").pyclass

removeAsynchronousHashRequest = GED("http://hasher.ws.lockss.org/", "removeAsynchronousHashRequest").pyclass

removeAsynchronousHashRequestResponse = GED("http://hasher.ws.lockss.org/", "removeAsynchronousHashRequestResponse").pyclass
