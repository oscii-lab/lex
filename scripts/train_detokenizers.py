#!/usr/bin/env python3

"""Train detokenizers. Run from lex root directory."""

import subprocess

langs = ['de', 'es', 'fr']
raw = 'data/detok/detok_sample_1M_{}_raw.clean.gz'
tok = 'data/detok/detok_sample_1M_{}_raw.clean.tok.gz'
out = 'data/detok/detok_sample_1M_{}_raw.clean.detokenizer'
java = 'java -cp build/install/lex/lib/*'.split()
mem =  '-Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication'.split()
cls = ['org.oscii.detokenize.TrainDetokenizer']

if __name__ == '__main__':
    #subprocess.check_call(['gradle', 'installDist'])
    for lang in langs:
        print('Training detokenizer for ' + lang)
        opt = ['-raw', raw.format(lang), '-tok', tok.format(lang), '-out', out.format(lang)]
        opt += ['-trainsize', '10000', '-regularization', '10']
        cmd = java + mem + cls + opt
        subprocess.check_call(cmd)
        
        
