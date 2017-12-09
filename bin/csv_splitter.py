#!/usr/bin/env python3

import os
import sys
import csv
import re
import argparse
from unidecode import unidecode

# use as remove_non_ascii("...")
def remove_non_ascii(text):
    return unidecode(str(text))

def replace_trash(unicode_string):
     for i in range(0, len(unicode_string)):
         try:
             unicode_string[i].encode("ascii")
         except:
              #means it's non-ASCII
              unicode_string=unicode_string[i].replace(" ") #replacing it with a single space
     return unicode_string

def split(filehandler, delimiter=',', row_limit=10000, 
          output_name_template='output_%s.csv', output_path='.', keep_headers=True,
          remove_all=False, zap_quotes_commas=False):
    """
    Splits a CSV file into multiple pieces.
    
    A quick bastardization of the Python CSV library.
    Arguments:
        `row_limit`: The number of rows you want in each output file. 10,000 by default.
        `output_name_template`: A %s-style template for the numbered output files.
        `output_path`: Where to stick the output files.
        `keep_headers`: Whether or not to print the headers in each output file.
    Example usage:
    
        >> from toolbox import csv_splitter;
        >> csv_splitter.split(open('/home/ben/input.csv', 'r'));
    
    """
    csv.field_size_limit(sys.maxsize)
    reader = csv.reader(filehandler, delimiter=delimiter)
    current_piece = 1
    current_out_path = os.path.join(
        output_path,
        output_name_template  % current_piece
    )
    current_out_writer = csv.writer(open(current_out_path, 'w'), dialect=csv.excel)#, quoting=csv.QUOTE_ALL)
    current_limit = row_limit
    real_line = 0
    if keep_headers:
        headers = next(reader)
        current_out_writer.writerow(headers)
    for i, row in enumerate(reader):
        #row = [s.replace('\0', "X") for s in row] # must be zapped prior
        #row = [re.sub(r'[\x00-\x1f\x7f-\x9f]', r' ', s) for s in row]
        row = [remove_non_ascii(s) for s in row]
        # keep LF, rm CR
        row = [re.sub(r'[\x00-\x09\x0b-\x1f\x7f-\x9f]', r' ', s) for s in row]
        # this used to be included by default...to handle genies
        if(zap_quotes_commas):
            row = [s.replace('"', ' ') for s in row]
            row = [s.replace(',', ' ') for s in row]
        #row = [replace_trash(s) for s in row]
        #row = [s.replaceAll("[^\\P{Cc}\\t\\r\\n]", "") for s in row]
        #print("{} - {}".format(i, row))
        if i + 1 > current_limit:
            current_piece += 1
            current_limit = row_limit * current_piece
            current_out_path = os.path.join(
                output_path,
                output_name_template  % current_piece
            )
            current_out_writer = csv.writer(open(current_out_path, 'w'), dialect=csv.excel)#, quoting=csv.QUOTE_ALL)
            if keep_headers:
                current_out_writer.writerow(headers)
        current_out_writer.writerow(row)
    
##
## split(open('/your/pat/input.csv', 'r'), row_limit=40000)
##
if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="CSV row cutter and cleaner")
    parser.add_argument("file", help="Input file.")
    parser.add_argument("--rows", type=int, help="Number of rows per chunk.", default=15000)
    parser.add_argument("--zap-quotes-commas", action='store_true',
                        help="Remove quotes and commas",
                        default=False)
    parser.add_argument("--remove-all", action='store_true',
                        help="Remove all bad chars from strings.", default=False)
    args = parser.parse_args()
    
    split(open(args.file), row_limit=args.rows,
          output_name_template=args.file + '_%s.csv',
          remove_all=args.remove_all,
          zap_quotes_commas=args.zap_quotes_commas)
