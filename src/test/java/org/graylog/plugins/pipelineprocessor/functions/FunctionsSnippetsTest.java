/**
 * This file is part of Graylog Pipeline Processor.
 *
 * Graylog Pipeline Processor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog Pipeline Processor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog Pipeline Processor.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.pipelineprocessor.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.graylog.plugins.pipelineprocessor.BaseParserTest;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.Rule;
import org.graylog.plugins.pipelineprocessor.ast.functions.Function;
import org.graylog.plugins.pipelineprocessor.functions.conversion.BooleanConversion;
import org.graylog.plugins.pipelineprocessor.functions.conversion.DoubleConversion;
import org.graylog.plugins.pipelineprocessor.functions.conversion.LongConversion;
import org.graylog.plugins.pipelineprocessor.functions.conversion.StringConversion;
import org.graylog.plugins.pipelineprocessor.functions.dates.FlexParseDate;
import org.graylog.plugins.pipelineprocessor.functions.dates.FormatDate;
import org.graylog.plugins.pipelineprocessor.functions.dates.Now;
import org.graylog.plugins.pipelineprocessor.functions.dates.ParseDate;
import org.graylog.plugins.pipelineprocessor.functions.hashing.CRC32;
import org.graylog.plugins.pipelineprocessor.functions.hashing.CRC32C;
import org.graylog.plugins.pipelineprocessor.functions.hashing.MD5;
import org.graylog.plugins.pipelineprocessor.functions.hashing.Murmur3_128;
import org.graylog.plugins.pipelineprocessor.functions.hashing.Murmur3_32;
import org.graylog.plugins.pipelineprocessor.functions.hashing.SHA1;
import org.graylog.plugins.pipelineprocessor.functions.hashing.SHA256;
import org.graylog.plugins.pipelineprocessor.functions.hashing.SHA512;
import org.graylog.plugins.pipelineprocessor.functions.ips.CidrMatch;
import org.graylog.plugins.pipelineprocessor.functions.ips.IpAddressConversion;
import org.graylog.plugins.pipelineprocessor.functions.json.JsonParse;
import org.graylog.plugins.pipelineprocessor.functions.json.SelectJsonPath;
import org.graylog.plugins.pipelineprocessor.functions.messages.CreateMessage;
import org.graylog.plugins.pipelineprocessor.functions.messages.DropMessage;
import org.graylog.plugins.pipelineprocessor.functions.messages.HasField;
import org.graylog.plugins.pipelineprocessor.functions.messages.RemoveField;
import org.graylog.plugins.pipelineprocessor.functions.messages.RouteToStream;
import org.graylog.plugins.pipelineprocessor.functions.messages.SetField;
import org.graylog.plugins.pipelineprocessor.functions.messages.SetFields;
import org.graylog.plugins.pipelineprocessor.functions.strings.Abbreviate;
import org.graylog.plugins.pipelineprocessor.functions.strings.Capitalize;
import org.graylog.plugins.pipelineprocessor.functions.strings.Contains;
import org.graylog.plugins.pipelineprocessor.functions.strings.GrokMatch;
import org.graylog.plugins.pipelineprocessor.functions.strings.Lowercase;
import org.graylog.plugins.pipelineprocessor.functions.strings.RegexMatch;
import org.graylog.plugins.pipelineprocessor.functions.strings.Substring;
import org.graylog.plugins.pipelineprocessor.functions.strings.Swapcase;
import org.graylog.plugins.pipelineprocessor.functions.strings.Uncapitalize;
import org.graylog.plugins.pipelineprocessor.functions.strings.Uppercase;
import org.graylog.plugins.pipelineprocessor.parser.FunctionRegistry;
import org.graylog.plugins.pipelineprocessor.parser.ParseException;
import org.graylog2.grok.GrokPattern;
import org.graylog2.grok.GrokPatternService;
import org.graylog2.plugin.InstantMillisProvider;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.graylog2.streams.StreamService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FunctionsSnippetsTest extends BaseParserTest {

    public static final DateTime GRAYLOG_EPOCH = DateTime.parse("2010-07-30T16:03:25Z");

    @BeforeClass
    public static void registerFunctions() {
        final Map<String, Function<?>> functions = commonFunctions();

        functions.put(BooleanConversion.NAME, new BooleanConversion());
        functions.put(DoubleConversion.NAME, new DoubleConversion());
        functions.put(LongConversion.NAME, new LongConversion());
        functions.put(StringConversion.NAME, new StringConversion());

        // message related functions
        functions.put(HasField.NAME, new HasField());
        functions.put(SetField.NAME, new SetField());
        functions.put(SetFields.NAME, new SetFields());
        functions.put(RemoveField.NAME, new RemoveField());

        functions.put(DropMessage.NAME, new DropMessage());
        functions.put(CreateMessage.NAME, new CreateMessage());

        // route to stream mocks
        final StreamService streamService = mock(StreamService.class);
        final Stream stream = mock(Stream.class);
        when(stream.isPaused()).thenReturn(false);
        when(stream.getTitle()).thenReturn("some name");
        when(stream.getId()).thenReturn("id");
        when(streamService.loadAll()).thenReturn(Lists.newArrayList(stream));

        functions.put(RouteToStream.NAME, new RouteToStream(streamService));

        // input related functions
        // TODO needs mock
        //functions.put(FromInput.NAME, new FromInput());

        // generic functions
        functions.put(RegexMatch.NAME, new RegexMatch());

        // string functions
        functions.put(Abbreviate.NAME, new Abbreviate());
        functions.put(Capitalize.NAME, new Capitalize());
        functions.put(Contains.NAME, new Contains());
        functions.put(Lowercase.NAME, new Lowercase());
        functions.put(Substring.NAME, new Substring());
        functions.put(Swapcase.NAME, new Swapcase());
        functions.put(Uncapitalize.NAME, new Uncapitalize());
        functions.put(Uppercase.NAME, new Uppercase());

        final ObjectMapper objectMapper = new ObjectMapperProvider().get();
        functions.put(JsonParse.NAME, new JsonParse(objectMapper));
        functions.put(SelectJsonPath.NAME, new SelectJsonPath(objectMapper));

        functions.put(Now.NAME, new Now());
        functions.put(FlexParseDate.NAME, new FlexParseDate());
        functions.put(ParseDate.NAME, new ParseDate());
        functions.put(FormatDate.NAME, new FormatDate());

        functions.put(CRC32.NAME, new CRC32());
        functions.put(CRC32C.NAME, new CRC32C());
        functions.put(MD5.NAME, new MD5());
        functions.put(Murmur3_32.NAME, new Murmur3_32());
        functions.put(Murmur3_128.NAME, new Murmur3_128());
        functions.put(SHA1.NAME, new SHA1());
        functions.put(SHA256.NAME, new SHA256());
        functions.put(SHA512.NAME, new SHA512());

        functions.put(IpAddressConversion.NAME, new IpAddressConversion());
        functions.put(CidrMatch.NAME, new CidrMatch());

        functions.put(IsNull.NAME, new IsNull());
        functions.put(IsNotNull.NAME, new IsNotNull());

        final GrokPatternService grokPatternService = mock(GrokPatternService.class);
        Set<GrokPattern> patterns = Sets.newHashSet(
                GrokPattern.create("GREEDY", ".*"),
                GrokPattern.create("BASE10NUM", "(?<![0-9.+-])(?>[+-]?(?:(?:[0-9]+(?:\\.[0-9]+)?)|(?:\\.[0-9]+)))"),
                GrokPattern.create("NUMBER", "(?:%{BASE10NUM:UNWANTED})")
        );
        when(grokPatternService.loadAll()).thenReturn(patterns);
        functions.put(GrokMatch.NAME, new GrokMatch(grokPatternService, Executors.newScheduledThreadPool(1)));

        functionRegistry = new FunctionRegistry(functions);
    }

    @Test
    public void jsonpath() {
        final String json = "{\n" +
                "    \"store\": {\n" +
                "        \"book\": [\n" +
                "            {\n" +
                "                \"category\": \"reference\",\n" +
                "                \"author\": \"Nigel Rees\",\n" +
                "                \"title\": \"Sayings of the Century\",\n" +
                "                \"price\": 8.95\n" +
                "            },\n" +
                "            {\n" +
                "                \"category\": \"fiction\",\n" +
                "                \"author\": \"Evelyn Waugh\",\n" +
                "                \"title\": \"Sword of Honour\",\n" +
                "                \"price\": 12.99\n" +
                "            },\n" +
                "            {\n" +
                "                \"category\": \"fiction\",\n" +
                "                \"author\": \"Herman Melville\",\n" +
                "                \"title\": \"Moby Dick\",\n" +
                "                \"isbn\": \"0-553-21311-3\",\n" +
                "                \"price\": 8.99\n" +
                "            },\n" +
                "            {\n" +
                "                \"category\": \"fiction\",\n" +
                "                \"author\": \"J. R. R. Tolkien\",\n" +
                "                \"title\": \"The Lord of the Rings\",\n" +
                "                \"isbn\": \"0-395-19395-8\",\n" +
                "                \"price\": 22.99\n" +
                "            }\n" +
                "        ],\n" +
                "        \"bicycle\": {\n" +
                "            \"color\": \"red\",\n" +
                "            \"price\": 19.95\n" +
                "        }\n" +
                "    },\n" +
                "    \"expensive\": 10\n" +
                "}";

        final Rule rule = parser.parseRule(ruleForTest(), false);
        final Message message = evaluateRule(rule, new Message(json, "test", Tools.nowUTC()));

        assertThat(message.hasField("author_first")).isTrue();
        assertThat(message.hasField("author_last")).isTrue();

    }

    @Test
    public void substring() {
        final Rule rule = parser.parseRule(ruleForTest(), false);
        evaluateRule(rule);

        assertThat(actionsTriggered.get()).isTrue();
    }

    @Test
    public void dates() {
        final InstantMillisProvider clock = new InstantMillisProvider(GRAYLOG_EPOCH);
        DateTimeUtils.setCurrentMillisProvider(clock);

        try {
            final Rule rule;
            try {
                rule = parser.parseRule(ruleForTest(), false);
            } catch (ParseException e) {
                fail("Should not fail to parse", e);
                return;
            }
            final Message message = evaluateRule(rule);

            assertThat(actionsTriggered.get()).isTrue();
            assertThat(message).isNotNull();
            assertThat(message).isNotEmpty();
            assertThat(message.hasField("year")).isTrue();
            assertThat(message.getField("year")).isEqualTo(2010);
            assertThat(message.getField("timezone")).isEqualTo("UTC");
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test
    public void digests() {
        final Rule rule = parser.parseRule(ruleForTest(), false);
        evaluateRule(rule);

        assertThat(actionsTriggered.get()).isTrue();
    }

    @Test
    public void regexMatch() {
        try {
            final Rule rule = parser.parseRule(ruleForTest(), false);
            final Message message = evaluateRule(rule);
            assertNotNull(message);
            assertTrue(message.hasField("matched_regex"));
            assertTrue(message.hasField("group_1"));
        } catch (ParseException e) {
            Assert.fail("Should parse");
        }
    }

    @Test
    public void strings() {
        final Rule rule = parser.parseRule(ruleForTest(), false);
        final Message message = evaluateRule(rule);

        assertThat(actionsTriggered.get()).isTrue();
        assertThat(message).isNotNull();
        assertThat(message.getField("has_xyz")).isInstanceOf(Boolean.class);
        assertThat((boolean)message.getField("has_xyz")).isFalse();
    }

    @Test
    public void ipMatching() {
        final Rule rule = parser.parseRule(ruleForTest(), false);
        final Message in = new Message("test", "test", Tools.nowUTC());
        in.addField("ip", "192.168.1.20");
        final Message message = evaluateRule(rule, in);

        assertThat(actionsTriggered.get()).isTrue();
        assertThat(message).isNotNull();
        assertThat(message.getField("ip_anon")).isEqualTo("192.168.1.0");
        assertThat(message.getField("ipv6_anon")).isEqualTo("2001:db8::");
    }

    @Test
    public void evalError() {
        final Rule rule = parser.parseRule(ruleForTest(), false);

        final EvaluationContext context = contextForRuleEval(rule, new Message("test", "test", Tools.nowUTC()));

        assertThat(context).isNotNull();
        assertThat(context.hasEvaluationErrors()).isTrue();
        assertThat(Iterables.getLast(context.evaluationErrors()).toString()).isEqualTo("In call to function 'to_ip' at 5:28 an exception was thrown: 'null' is not an IP string literal.");
    }

    @Test
    public void evalErrorSuppressed() {
        final Rule rule = parser.parseRule(ruleForTest(), false);

        final Message message = new Message("test", "test", Tools.nowUTC());
        message.addField("this_field_was_set", true);
        final EvaluationContext context = contextForRuleEval(rule, message);

        assertThat(context).isNotNull();
        assertThat(context.hasEvaluationErrors()).isFalse();
        assertThat(actionsTriggered.get()).isTrue();
    }

    @Test
    public void newlyCreatedMessage() {
        final Rule rule = parser.parseRule(ruleForTest(), false);
        final EvaluationContext context = contextForRuleEval(rule, new Message("test", "test", Tools.nowUTC()));

        final Message origMessage = context.currentMessage();
        final Message newMessage = Iterables.getOnlyElement(context.createdMessages());

        assertThat(origMessage).isNotSameAs(newMessage);
        assertThat(newMessage.hasField("removed_again")).isFalse();
        assertThat(newMessage.getFieldAs(Boolean.class, "has_source")).isTrue();
        assertThat(newMessage.getFieldAs(String.class, "only_in")).isEqualTo("new message");
        assertThat(newMessage.getFieldAs(String.class, "multi")).isEqualTo("new message");

    }

    @Test
    public void grok() {
        final Rule rule = parser.parseRule(ruleForTest(), false);
        final Message message = evaluateRule(rule);

        assertThat(message).isNotNull();
        assertThat(message.getFieldCount()).isEqualTo(4);
        assertThat(message.getTimestamp()).isEqualTo(DateTime.parse("2015-07-31T10:05:36.773Z"));
    }
}
