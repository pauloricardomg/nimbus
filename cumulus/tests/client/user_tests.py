import string
import random
import os
import sys
import nose.tools
import boto
from boto.s3.connection import OrdinaryCallingFormat
from boto.s3.connection import VHostCallingFormat
from boto.s3.connection import SubdomainCallingFormat
import sys
from ConfigParser import SafeConfigParser
from pycb.cumulus import *
import time
import pycb.test_common
import unittest
import tempfile
import filecmp
import pycb.tools.add_user
import pycb.tools.list_users
import pycb.tools.remove_user
import pycb.tools.set_quota
#
class TestAddUsers(unittest.TestCase):

    def setUp(self):
        self.user_name = "test@nosetests.nimbus.org"
        pycb.tools.remove_user.main(["-a", self.user_name])
        pass

    def tearDown(self):
        pycb.tools.remove_user.main(["-a", self.user_name])
        pass

    def find_in_file(self, fname, needle):
        found = False
        f = open(fname)
        l = f.readline()
        while l:
            print "#### " + l
            x = l.find(needle)
            if x >= 0:
                found = True
            l = f.readline()
        f.close()
        os.unlink(fname)
        return found

    def test_new_user(self):
        rc = pycb.tools.add_user.main([self.user_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))
        rc = pycb.tools.remove_user.main(["-a", self.user_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

    def test_list_user(self):
        display_name = str(uuid.uuid1())
        rc = pycb.tools.add_user.main([display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        (tmpFD, outFileName) = tempfile.mkstemp("cumulustests")
        os.close(tmpFD)

        rc = pycb.tools.list_users.main(["-O", outFileName, '*'])
        self.assertEqual(rc, 0, "rc = %d" % (rc))
        rc = self.find_in_file(outFileName, display_name)
        self.assertTrue(rc, "display name not found in list %s" % (display_name))

        rc = pycb.tools.remove_user.main(["-a", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

    def test_column_report(self):
        display_name = str(uuid.uuid1())
        rc = pycb.tools.add_user.main([display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        (tmpFD, outFileName) = tempfile.mkstemp("cumulustests")
        os.close(tmpFD)

        rc = pycb.tools.list_users.main(["-O", outFileName, "-b", "-r", "friendly,quota", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        n = "%s,None" % (display_name)
        rc = self.find_in_file(outFileName, display_name)
        self.assertTrue(rc, "display name not found in list")

        rc = pycb.tools.list_users.main(["-O", outFileName, "-b", "-r", "quota,friendly,quota", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        n = "None,%s,None" % (display_name)
        rc = self.find_in_file(outFileName, display_name)
        self.assertTrue(rc, "display name not found in list")
        rc = pycb.tools.remove_user.main(["-a", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

    def test_quota(self):
        display_name = str(uuid.uuid1())
        rc = pycb.tools.add_user.main([display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        (tmpFD, outFileName) = tempfile.mkstemp("cumulustests")
        os.close(tmpFD)

        q = "1000"
        rc = pycb.tools.set_quota.main([display_name, q])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.list_users.main(["-O", outFileName, "-b", "-r", "friendly,quota", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        n = "%s,%s" % (display_name, q)
        rc = self.find_in_file(outFileName, display_name)
        self.assertTrue(rc, "display name not found in list")

        rc = pycb.tools.remove_user.main(["-a", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

    def test_error_cmdline(self):
        rc = pycb.tools.add_user.main([])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.remove_user.main([])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.set_quota.main([])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.list_users.main([])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

    def test_help(self):
        rc = pycb.tools.add_user.main(["--help"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.remove_user.main(["--help"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.set_quota.main(["--help"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.list_users.main(["--help"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.add_user.main(["-h"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.remove_user.main(["-h"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.set_quota.main(["-h"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.list_users.main(["-h"])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

    def test_user_not_found(self):
        display_name = str(uuid.uuid1())
        rc = pycb.tools.remove_user.main([display_name])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.set_quota.main([display_name])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

    def test_quota_bad_cmd(self):
        display_name = str(uuid.uuid1())
        rc = pycb.tools.add_user.main([display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.set_quota.main([display_name])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.set_quota.main([display_name, "BAD"])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.set_quota.main([display_name, "\-10"])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))


    def test_quota_unlimited(self):
        display_name = str(uuid.uuid1())
        rc = pycb.tools.add_user.main([display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        (tmpFD, outFileName) = tempfile.mkstemp("cumulustests")
        os.close(tmpFD)

        q = "UNLIMITED"
        rc = pycb.tools.set_quota.main([display_name, q])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.list_users.main(["-O", outFileName, "-b", "-r", "friendly,quota", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        n = "%s,%s" % (display_name, q)
        rc = self.find_in_file(outFileName, display_name)
        self.assertTrue(rc, "display name not found in list")

        rc = pycb.tools.remove_user.main(["-a", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

    def test_add_user(self):
        display_name = str(uuid.uuid1())
        rc = pycb.tools.add_user.main([display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        rc = pycb.tools.add_user.main(["-e", "-p", "hello", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

        (tmpFD, outFileName) = tempfile.mkstemp("cumulustests")
        os.close(tmpFD)
        rc = pycb.tools.list_users.main(["-O", outFileName, "-b", "-r", "password", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))
        rc = self.find_in_file(outFileName, "hello")

        rc = pycb.tools.remove_user.main(["-a", display_name])
        self.assertEqual(rc, 0, "rc = %d" % (rc))

    def test_add_user_unknown(self):
        rc = pycb.tools.add_user.main(["-e", "hello"])
        self.assertNotEqual(rc, 0, "rc = %d" % (rc))

